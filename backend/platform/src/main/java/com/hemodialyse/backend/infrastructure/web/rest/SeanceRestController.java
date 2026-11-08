package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.domain.patient.service.FinOccupation;
import com.hemodialyse.backend.domain.seance.model.DerogationPlanning;
import com.hemodialyse.backend.domain.seance.model.MotifHorsPlanning;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.CurrentUser;
import com.hemodialyse.backend.infrastructure.web.dto.request.*;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.validation.Valid;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/v1/seances")
public class SeanceRestController {

    /**
     * Statuts d'une séance réalisée (présence du patient) ; commence par une espace pour suivre {@code IN}.
     */
    private static final String SEANCES_REALISEES = " ('VALIDEE', 'SIGNEE', 'FACTUREE')";

    private final SeanceUseCase seanceUseCase;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    private final CenterAccessGuard centerAccessGuard;
    private final AbsencesDuPilotage absencesDuPilotage;
    private final com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance consommationsStock;

    public SeanceRestController(SeanceUseCase seanceUseCase,
                                NotificationService notificationService,
                                JdbcTemplate jdbc,
                                CenterAccessGuard centerAccessGuard,
                                AbsencesDuPilotage absencesDuPilotage,
                                com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance consommationsStock) {
        this.absencesDuPilotage = absencesDuPilotage;
        this.seanceUseCase = seanceUseCase;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
        this.centerAccessGuard = centerAccessGuard;
        this.consommationsStock = consommationsStock;
    }

    // Création, scan, consultation et liste des séances : servies par `contexts/seance` (SeancesControleur, SE1, ADR 0088).
    // SE5 (ADR 0092) : la surcharge du forfait d'une séance (PUT /{id}/forfait) est servie par `contexts/seance` (ImposerUnForfait) ;
    // le forfait courant se lit par le langage publié ForfaitsFournis.

    // SE6 (ADR 0093) : le changement de jour (PUT /{id}) et la suppression (DELETE /{id}) sont servis par `contexts/seance`
    // (SeancesControleur, ChangerLeJourDUneSeance, SupprimerUneSeance).

    // SE2 : valider, signer-medecin et deverrouiller-regularisation sont servis par contexts/seance (SeancesControleur, ADR 0089).

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/journal")
    public ResponseEntity<?> journalByDate(@RequestParam UUID centerId,
                                           @RequestParam LocalDate dateSeance) {
        centerAccessGuard.requireCenter(centerId); // revue DM1 : le centre demandé doit être celui du jeton (403 sinon)
        var patients = jdbc.query(
                """
                        SELECT s.id AS seance_id,
                               s.patient_id AS patient_id,
                               s.date_seance AS date_seance,
                               s.statut AS statut,
                               p.code_patient AS code_patient,
                               p.nom AS nom,
                               p.prenom AS prenom
                        FROM seances s
                        INNER JOIN patients p ON p.id = s.patient_id
                        WHERE s.center_id = ?
                          AND s.date_seance = ?
                        ORDER BY p.nom ASC, p.prenom ASC
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("seanceId", rs.getObject("seance_id", UUID.class));
                    row.put("patientId", rs.getObject("patient_id", UUID.class));
                    row.put("dateSeance", rs.getObject("date_seance", Date.class).toLocalDate());
                    row.put("status", rs.getString("statut"));
                    row.put("patientCode", rs.getString("code_patient"));
                    row.put("patientNom", rs.getString("nom"));
                    row.put("patientPrenom", rs.getString("prenom"));
                    return row;
                },
                centerId,
                Date.valueOf(dateSeance)
        );

        var articles = new java.util.ArrayList<Map<String, Object>>();
        for (var s : consommationsStock.sortiesParDate(centerId, dateSeance)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("articleId", s.articleId());
            row.put("articleCode", s.articleCode());
            row.put("articleLibelle", s.articleLibelle());
            row.put("quantiteTotale", s.quantiteTotale());
            articles.add(row);
        }

        var payload = new LinkedHashMap<String, Object>();
        payload.put("dateSeance", dateSeance);
        payload.put("patients", patients);
        payload.put("sortiesArticles", articles);
        return ResponseEntity.ok(payload);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam UUID centerId,
                                       @RequestParam int year,
                                       @RequestParam int month) {
        // KB-ABS-05 : un centre autre que celui de la session est refusé (403), comme /absences-patients
        centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(buildSeanceDashboard(centerId, year, month));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard/details")
    public ResponseEntity<?> dashboardDetails(@RequestParam UUID centerId,
                                              @RequestParam int year,
                                              @RequestParam int month,
                                              @RequestParam String kind) {
        centerAccessGuard.requireCenter(centerId); // KB-ABS-05
        return ResponseEntity.ok(buildSeanceDashboardDetails(centerId, year, month, kind));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard/export")
    public ResponseEntity<?> exportDashboard(@RequestParam UUID centerId,
                                             @RequestParam int year,
                                             @RequestParam int month,
                                             @RequestParam(defaultValue = "csv") String format) {
        centerAccessGuard.requireCenter(centerId); // KB-ABS-05 (même donnée que le tableau de bord)
        if (!List.of("csv", "pdf", "xlsx").contains(format.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("format must be 'csv', 'pdf' or 'xlsx'"); // KB-ABS-36 : 400
        }
        Map<String, Object> dashboard = buildSeanceDashboard(centerId, year, month);
        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdf = exportDashboardPdf(dashboard);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=seances-dashboard-" + year + "-" + String.format("%02d", month) + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }

        if ("xlsx".equalsIgnoreCase(format)) {
            byte[] xlsx = exportDashboardXlsx(dashboard);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=seances-dashboard-" + year + "-" + String.format("%02d", month) + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(xlsx);
        }

        String csv = exportDashboardCsv(dashboard);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=seances-dashboard-" + year + "-" + String.format("%02d", month) + ".csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/calendar")
    public ResponseEntity<?> calendar(@RequestParam UUID centerId,
                                      @RequestParam int year,
                                      @RequestParam int month) {
        centerAccessGuard.requireCenter(centerId); // revue DM1 : le centre demandé doit être celui du jeton (403 sinon)
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        var holidays = jdbc.query(
                "SELECT id, day_date, label FROM center_holiday WHERE center_id = ? AND day_date BETWEEN ? AND ? ORDER BY day_date ASC",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class));
                    row.put("dayDate", rs.getObject("day_date", Date.class).toLocalDate());
                    row.put("label", rs.getString("label"));
                    return row;
                },
                centerId,
                Date.valueOf(from),
                Date.valueOf(to)
        );

        var closures = jdbc.query(
                "SELECT id, day_date, reason FROM center_closure_day WHERE center_id = ? AND day_date BETWEEN ? AND ? ORDER BY day_date ASC",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class));
                    row.put("dayDate", rs.getObject("day_date", Date.class).toLocalDate());
                    row.put("reason", rs.getString("reason"));
                    return row;
                },
                centerId,
                Date.valueOf(from),
                Date.valueOf(to)
        );

        return ResponseEntity.ok(Map.of(
                "year", year,
                "month", month,
                "holidays", holidays,
                "closures", closures
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/calendar/holiday")
    public ResponseEntity<?> addHoliday(@RequestBody @Valid SeanceCalendarDayRequest request) {
        centerAccessGuard.requireCenter(request.centerId()); // KB-MED-02 : le centre du corps doit être celui du jeton (403 sinon)
        UUID id = UUID.randomUUID();
        jdbc.update(
                "MERGE INTO center_holiday (id, center_id, day_date, label) KEY (id) VALUES (?, ?, ?, ?)",
                id,
                request.centerId(),
                Date.valueOf(request.dayDate()),
                request.labelOrReason()
        );
        return ResponseEntity.ok(Map.of("id", id, "dayDate", request.dayDate()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/calendar/holiday/{id}")
    public ResponseEntity<?> deleteHoliday(@PathVariable UUID id,
                                           @RequestParam UUID centerId) {
        centerAccessGuard.requireCenter(centerId); // KB-MED-02
        jdbc.update("DELETE FROM center_holiday WHERE id = ? AND center_id = ?", id, centerId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/calendar/closure")
    public ResponseEntity<?> addClosure(@RequestBody @Valid SeanceCalendarDayRequest request) {
        centerAccessGuard.requireCenter(request.centerId()); // KB-MED-02
        UUID id = UUID.randomUUID();
        jdbc.update(
                "MERGE INTO center_closure_day (id, center_id, day_date, reason) KEY (id) VALUES (?, ?, ?, ?)",
                id,
                request.centerId(),
                Date.valueOf(request.dayDate()),
                request.labelOrReason()
        );
        return ResponseEntity.ok(Map.of("id", id, "dayDate", request.dayDate()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/calendar/closure/{id}")
    public ResponseEntity<?> deleteClosure(@PathVariable UUID id,
                                           @RequestParam UUID centerId) {
        centerAccessGuard.requireCenter(centerId); // KB-MED-02
        jdbc.update("DELETE FROM center_closure_day WHERE id = ? AND center_id = ?", id, centerId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private Map<String, Object> buildSeanceDashboard(UUID centerId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // Présences : séances réalisées (validées, signées ou facturées) du mois
        Long presenceCountRaw = jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND date_seance BETWEEN ? AND ? AND statut IN " + SEANCES_REALISEES,
                Long.class,
                centerId,
                Date.valueOf(from),
                Date.valueOf(to)
        );
        long presenceCount = presenceCountRaw == null ? 0L : presenceCountRaw;

        // Séances prévues et absences : jours échus seulement (le futur n'est ni présence ni absence) ; « aujourd'hui » est le jour
        // du fuseau des centres (KB-ABS-34)
        LocalDate aujourdhui = absencesDuPilotage.aujourdhui();
        LocalDate limite = to.isAfter(aujourdhui) ? aujourdhui : to;
        // Absences : celles du suivi des absences (déclarées ou détectées par le contrôle quotidien), hors annulées, jusqu'à
        // aujourd'hui (KB-ABS-35 : un mois futur n'en compte aucune)
        long absences = limite.isBefore(from) ? 0L : absencesDuPilotage.nombreHorsAnnulees(centerId, from, limite);

        // Jours non ouvrés centre (fériés + fermetures exceptionnelles)
        Set<LocalDate> blockedDates = new HashSet<>();
        blockedDates.addAll(loadBlockedDates("center_holiday", centerId, from, to));
        blockedDates.addAll(loadBlockedDates("center_closure_day", centerId, from, to));

        // Programmation patients (jours dialyse)
        List<Map<String, Object>> patients = jdbc.queryForList(
                """
                        SELECT id,
                               sexe,
                               date_naissance,
                               date_admission,
                               en_sommeil,
                               etat_patient,
                               date_evenement_etat,
                               jour_lundi,
                               jour_mardi,
                               jour_mercredi,
                               jour_jeudi,
                               jour_vendredi,
                               jour_samedi,
                               jour_dimanche
                        FROM patients
                        WHERE center_id = ?
                        """,
                centerId
        );

        // Effectif du mois : patients pris en charge sur la période (admis, non sortis, non en sommeil), qu'ils aient
        // déjà dialysé ou non, auxquels s'ajoutent ceux qui ont une séance réalisée dans le mois
        Map<Object, Map<String, Object>> effectif = new LinkedHashMap<>();

        long expectedSeances = 0L;
        long consideredPatients = 0L;
        Map<String, Long> expectedByWeekday = new LinkedHashMap<>();
        expectedByWeekday.put("LUNDI", 0L);
        expectedByWeekday.put("MARDI", 0L);
        expectedByWeekday.put("MERCREDI", 0L);
        expectedByWeekday.put("JEUDI", 0L);
        expectedByWeekday.put("VENDREDI", 0L);
        expectedByWeekday.put("SAMEDI", 0L);
        expectedByWeekday.put("DIMANCHE", 0L);
        for (Map<String, Object> patient : patients) {
            Boolean enSommeil = asBoolean(readColumn(patient, "en_sommeil"));
            if (Boolean.TRUE.equals(enSommeil)) {
                continue;
            }
            LocalDate admission = asLocalDate(readColumn(patient, "date_admission"));
            LocalDate effectiveStart = admission == null || admission.isBefore(from) ? from : admission;
            if (!effectiveStart.isAfter(derniereDateAttendue(patient, to))) {
                effectif.put(readColumn(patient, "id"), patient);
            }
            LocalDate effectiveEnd = derniereDateAttendue(patient, limite);
            if (effectiveStart.isAfter(effectiveEnd)) {
                continue;
            }
            consideredPatients++;
            for (LocalDate d = effectiveStart; !d.isAfter(effectiveEnd); d = d.plusDays(1)) {
                if (blockedDates.contains(d)) {
                    continue;
                }
                if (isPatientScheduledOn(patient, d)) {
                    expectedSeances++;
                    String key = d.getDayOfWeek().name();
                    expectedByWeekday.put(key, expectedByWeekday.getOrDefault(key, 0L) + 1L);
                }
            }
        }

        // Répartition sexe + âge de l'effectif du mois (patients pris en charge + patients présents)
        List<Map<String, Object>> presentPatients = jdbc.queryForList(
                """
                        SELECT DISTINCT p.id, p.sexe, p.date_naissance
                        FROM seances s
                        INNER JOIN patients p ON p.id = s.patient_id
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                          AND s.statut IN """ + SEANCES_REALISEES + "\n",
                centerId,
                Date.valueOf(from),
                Date.valueOf(to)
        );

        Map<String, Long> bySexe = new LinkedHashMap<>();
        bySexe.put("M", 0L);
        bySexe.put("F", 0L);
        bySexe.put("AUTRE", 0L);

        Map<String, Long> byAgeRange = new LinkedHashMap<>();
        byAgeRange.put("0-17", 0L);
        byAgeRange.put("18-39", 0L);
        byAgeRange.put("40-59", 0L);
        byAgeRange.put("60+", 0L);
        byAgeRange.put("INCONNU", 0L);

        LocalDate refDate = to;
        presentPatients.forEach(p -> effectif.putIfAbsent(readColumn(p, "id"), p));
        for (Map<String, Object> p : effectif.values()) {
            String sexe = normalizeSexe((String) readColumn(p, "sexe"));
            bySexe.put(sexe, bySexe.getOrDefault(sexe, 0L) + 1L);

            LocalDate naissance = asLocalDate(readColumn(p, "date_naissance"));
            String ageRange = toAgeRange(naissance, refDate);
            byAgeRange.put(ageRange, byAgeRange.getOrDefault(ageRange, 0L) + 1L);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("year", year);
        payload.put("month", month);
        payload.put("expectedSeances", expectedSeances);
        payload.put("presenceCount", presenceCount);
        payload.put("absenceCount", absences);
        payload.put("totalSeances", presenceCount);
        payload.put("effectifPatients", effectif.size());
        payload.put("absenceDetails", Map.of(
                "formula", "absences = absences du suivi (declarees ou detectees, hors annulees) ; presences = seances realisees",
                "periodStart", from,
                "periodEnd", limite.isBefore(from) ? from : limite,
                "patientsConsidered", consideredPatients,
                "blockedDays", blockedDates.size(),
                "expectedFromSchedule", expectedSeances,
                "presenceCount", presenceCount,
                "absenceCount", absences,
                "expectedByWeekday", expectedByWeekday
        ));
        payload.put("sexeDistribution", bySexe);
        payload.put("ageDistribution", byAgeRange);
        return payload;
    }

    private String exportDashboardCsv(Map<String, Object> dashboard) {
        @SuppressWarnings("unchecked")
        Map<String, Number> sexe = (Map<String, Number>) dashboard.getOrDefault("sexeDistribution", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Number> age = (Map<String, Number>) dashboard.getOrDefault("ageDistribution", Map.of());

        StringBuilder sb = new StringBuilder();
        sb.append("year,month,expectedSeances,presenceCount,absenceCount,totalSeances\n");
        sb.append(dashboard.getOrDefault("year", ""))
                .append(',').append(dashboard.getOrDefault("month", ""))
                .append(',').append(dashboard.getOrDefault("expectedSeances", 0))
                .append(',').append(dashboard.getOrDefault("presenceCount", 0))
                .append(',').append(dashboard.getOrDefault("absenceCount", 0))
                .append(',').append(dashboard.getOrDefault("totalSeances", 0)).append("\n\n");

        sb.append("sexe,count\n");
        sexe.forEach((k, v) -> sb.append(k).append(',').append(v).append("\n"));
        sb.append("\n");
        sb.append("ageRange,count\n");
        age.forEach((k, v) -> sb.append(k).append(',').append(v).append("\n"));
        return sb.toString();
    }

    private Map<String, Object> buildSeanceDashboardDetails(UUID centerId, int year, int month, String kind) {
        String normalizedKind = (kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT));
        if (!normalizedKind.equals("presence") && !normalizedKind.equals("absence")) {
            throw new IllegalArgumentException("kind must be 'presence' or 'absence'");
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // Présences : séances réalisées ; absences : suivi des absences (déclarées ou détectées), hors annulées
        boolean presence = normalizedKind.equals("presence");
        Map<UUID, Map<String, Object>> patients = new HashMap<>();
        jdbc.queryForList("SELECT id, nom, prenom FROM patients WHERE center_id = ?", centerId)
                .forEach(p -> patients.put((UUID) readColumn(p, "id"), p));

        List<Map<String, Object>> lignes;
        if (presence) {
            lignes = jdbc.queryForList("SELECT s.patient_id, s.date_seance, s.statut FROM seances s WHERE s.center_id = ? "
                    + "AND s.date_seance BETWEEN ? AND ? AND s.statut IN" + SEANCES_REALISEES,
                    centerId, Date.valueOf(from), Date.valueOf(to));
        } else {
            // jusqu'à aujourd'hui, jour du fuseau des centres (KB-ABS-34, 35 : un mois futur n'a aucune absence)
            LocalDate aujourdhui = absencesDuPilotage.aujourdhui();
            LocalDate limite = to.isAfter(aujourdhui) ? aujourdhui : to;
            lignes = new ArrayList<>();
            if (!limite.isBefore(from)) {
                for (AbsencesDuPilotage.AbsenceDuCentre a : absencesDuPilotage.horsAnnulees(centerId, from, limite)) {
                    Map<String, Object> ligne = new HashMap<>();
                    ligne.put("patient_id", a.patient());
                    ligne.put("date_seance", a.dateDeSeance());
                    ligne.put("statut", a.statut());
                    lignes.add(ligne);
                }
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> ligne : lignes) {
            UUID patientId = (UUID) readColumn(ligne, "patient_id");
            LocalDate d = asLocalDate(readColumn(ligne, "date_seance"));
            Map<String, Object> patient = patients.get(patientId);
            if (patient == null || d == null) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("patientId", patientId);
            row.put("patientNom", Objects.toString(readColumn(patient, "nom"), ""));
            row.put("patientPrenom", Objects.toString(readColumn(patient, "prenom"), ""));
            row.put("dateSeance", d);
            row.put("weekday", d.getDayOfWeek().name());
            row.put("scheduled", true);
            row.put("present", presence);
            row.put("status", Objects.toString(readColumn(ligne, "statut"), ""));
            items.add(row);
        }

        items.sort(Comparator
                .comparing((Map<String, Object> r) -> (LocalDate) r.get("dateSeance"))
                .thenComparing(r -> Objects.toString(r.get("patientNom"), ""))
                .thenComparing(r -> Objects.toString(r.get("patientPrenom"), "")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("year", year);
        payload.put("month", month);
        payload.put("kind", normalizedKind);
        payload.put("total", items.size());
        payload.put("items", items);
        return payload;
    }

    private byte[] exportDashboardPdf(Map<String, Object> dashboard) {
        String html = """
                <html><head><meta charset='UTF-8'/>
                <style>
                body{font-family:Arial,sans-serif;font-size:12px;color:#111;margin:20px;}
                h1{font-size:20px;margin:0 0 10px 0;} h2{font-size:15px;margin:16px 0 6px 0;}
                table{width:100%%;border-collapse:collapse;margin-top:8px;}
                th,td{border:1px solid #ddd;padding:6px;text-align:left;} th{background:#f5f7fb;}
                </style></head><body>
                <h1>Dashboard séances</h1>
                <p><strong>Période:</strong> %s-%s</p>
                <p><strong>Séances prévues:</strong> %s</p>
                <p><strong>Présences:</strong> %s</p>
                <p><strong>Absences:</strong> %s</p>
                <p><strong>Séances totales:</strong> %s</p>
                %s
                %s
                </body></html>
                """.formatted(
                dashboard.getOrDefault("year", ""),
                String.format("%02d", dashboard.getOrDefault("month", 1)),
                dashboard.getOrDefault("expectedSeances", 0),
                dashboard.getOrDefault("presenceCount", 0),
                dashboard.getOrDefault("absenceCount", 0),
                dashboard.getOrDefault("totalSeances", 0),
                toPdfTable("Répartition sexe", "Sexe", castMap(dashboard.get("sexeDistribution"))),
                toPdfTable("Répartition âge", "Tranche", castMap(dashboard.get("ageDistribution")))
        );

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer le PDF dashboard séances", e);
        }
    }

    private byte[] exportDashboardXlsx(Map<String, Object> dashboard) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var summary = workbook.createSheet("Synthese");
            int row = 0;
            row = writePair(summary, row, "Annee", String.valueOf(dashboard.getOrDefault("year", "")));
            row = writePair(summary, row, "Mois", String.valueOf(dashboard.getOrDefault("month", "")));
            row = writePair(summary, row, "Seances prevues", String.valueOf(dashboard.getOrDefault("expectedSeances", 0)));
            row = writePair(summary, row, "Presences", String.valueOf(dashboard.getOrDefault("presenceCount", 0)));
            row = writePair(summary, row, "Absences", String.valueOf(dashboard.getOrDefault("absenceCount", 0)));
            writePair(summary, row, "Seances totales", String.valueOf(dashboard.getOrDefault("totalSeances", 0)));

            var sexeSheet = workbook.createSheet("Repartition sexe");
            writeDistribution(sexeSheet, "Sexe", castMap(dashboard.get("sexeDistribution")));

            var ageSheet = workbook.createSheet("Repartition age");
            writeDistribution(ageSheet, "Tranche age", castMap(dashboard.get("ageDistribution")));

            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);
            sexeSheet.autoSizeColumn(0);
            sexeSheet.autoSizeColumn(1);
            ageSheet.autoSizeColumn(0);
            ageSheet.autoSizeColumn(1);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer l'export XLSX dashboard séances", e);
        }
    }

    private int writePair(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, String key, String value) {
        var row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private void writeDistribution(org.apache.poi.ss.usermodel.Sheet sheet,
                                   String firstColumnLabel,
                                   Map<String, Number> data) {
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue(firstColumnLabel);
        header.createCell(1).setCellValue("Count");
        int row = 1;
        for (Map.Entry<String, Number> entry : data.entrySet()) {
            var r = sheet.createRow(row++);
            r.createCell(0).setCellValue(entry.getKey());
            r.createCell(1).setCellValue(entry.getValue().doubleValue());
        }
    }

    private Map<String, Number> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Number> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v instanceof Number n) {
                out.put(String.valueOf(k), n);
            }
        });
        return out;
    }

    private String toPdfTable(String title, String firstColumnTitle, Map<String, Number> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(title).append("</h2>");
        sb.append("<table><thead><tr><th>").append(firstColumnTitle).append("</th><th>Count</th></tr></thead><tbody>");
        if (data.isEmpty()) {
            sb.append("<tr><td colspan='2'>Aucune donnée</td></tr>");
        } else {
            data.forEach((k, v) -> sb.append("<tr><td>").append(k).append("</td><td>").append(v).append("</td></tr>"));
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private List<LocalDate> loadBlockedDates(String tableName, UUID centerId, LocalDate from, LocalDate to) {
        String sql = "SELECT day_date FROM " + tableName + " WHERE center_id = ? AND day_date BETWEEN ? AND ?";
        try {
            return jdbc.query(sql,
                    (rs, rowNum) -> rs.getObject("day_date", Date.class).toLocalDate(),
                    centerId,
                    Date.valueOf(from),
                    Date.valueOf(to));
        } catch (Exception ignored) {
            // Compatible avec les environnements où les tables calendrier ne sont pas encore créées.
            return List.of();
        }
    }

    /**
     * Dernier jour où le patient est attendu en séance dans la période : borné par la libération de sa place
     * (transfert, décès, greffe, guérison) ; {@link LocalDate#MIN} si l'état de sortie n'a pas de date.
     */
    private LocalDate derniereDateAttendue(Map<String, Object> patient, LocalDate to) {
        LocalDate fin = FinOccupation.dernierJourOccupe(
                (String) readColumn(patient, "etat_patient"),
                asLocalDate(readColumn(patient, "date_evenement_etat"))).orElse(to);
        return fin.isBefore(to) ? fin : to;
    }

    private boolean isPatientScheduledOn(Map<String, Object> patient, LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> asBoolean(readColumn(patient, "jour_lundi"));
            case TUESDAY -> asBoolean(readColumn(patient, "jour_mardi"));
            case WEDNESDAY -> asBoolean(readColumn(patient, "jour_mercredi"));
            case THURSDAY -> asBoolean(readColumn(patient, "jour_jeudi"));
            case FRIDAY -> asBoolean(readColumn(patient, "jour_vendredi"));
            case SATURDAY -> asBoolean(readColumn(patient, "jour_samedi"));
            case SUNDAY -> asBoolean(readColumn(patient, "jour_dimanche"));
        };
    }

    private Object readColumn(Map<String, Object> row, String name) {
        if (row.containsKey(name)) {
            return row.get(name);
        }
        String upper = name.toUpperCase();
        if (row.containsKey(upper)) {
            return row.get(upper);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.FALSE;
    }

    private LocalDate asLocalDate(Object value) {
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        return null;
    }

    private String normalizeSexe(String sexe) {
        if (sexe == null) {
            return "AUTRE";
        }
        String normalized = sexe.trim().toUpperCase();
        if (normalized.equals("M") || normalized.equals("H")) {
            return "M";
        }
        if (normalized.equals("F")) {
            return "F";
        }
        return "AUTRE";
    }

    private String toAgeRange(LocalDate birthDate, LocalDate refDate) {
        if (birthDate == null) {
            return "INCONNU";
        }
        long years = ChronoUnit.YEARS.between(birthDate, refDate);
        if (years < 18) {
            return "0-17";
        }
        if (years < 40) {
            return "18-39";
        }
        if (years < 60) {
            return "40-59";
        }
        return "60+";
    }

    public record SeanceCalendarDayRequest(UUID centerId,
                                           LocalDate dayDate,
                                           String labelOrReason) {
    }

    // SE3 (ADR 0090) : l'ajout, la modification et le retrait d'un consommable sont servis par contexts/seance (SeancesControleur).
}



