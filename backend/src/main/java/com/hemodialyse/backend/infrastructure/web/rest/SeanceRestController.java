package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/v1/seances")
public class SeanceRestController {

    private final SeanceUseCase seanceUseCase;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;

    public SeanceRestController(SeanceUseCase seanceUseCase,
                                NotificationService notificationService,
                                JdbcTemplate jdbc) {
        this.seanceUseCase = seanceUseCase;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateSeanceRequest request) {
        var seance = seanceUseCase.create(CenterId.of(request.centerId()), request.patientId(), request.dateSeance());

        var details = seanceUseCase.getDetails(CenterId.of(request.centerId()), seance.getId());
        var patient = details.patient();
        notificationService.notifySeanceCreated(
                request.centerId(),
                seance.getId(),
                seance.getPatientId(),
                patient.getNom(),
                patient.getPrenom(),
                seance.getDateSeance() == null ? null : seance.getDateSeance().toString()
        );

        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "dateSeance", seance.getDateSeance()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping
    public ResponseEntity<?> list(@RequestParam UUID centerId) {
        var items = seanceUseCase.list(CenterId.of(centerId));
        var payload = items.stream().map(item -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", item.id());
            row.put("centerId", item.centerId());
            row.put("patientId", item.patientId());
            row.put("patientCode", item.patientCode());
            row.put("patientNom", item.patientNom());
            row.put("patientPrenom", item.patientPrenom());
            row.put("dateSeance", item.dateSeance());
            row.put("status", item.status());
            row.put("createdAt", item.createdAt());
            row.put("validatedAt", item.validatedAt());
            row.put("signedByInfirmierAt", item.signedByInfirmierAt());
            row.put("signedByMedecinAt", item.signedByMedecinAt());
            row.put("forfait", loadCurrentForfait(centerId, item.patientId(), item.dateSeance()));
            return row;
        }).toList();
        return ResponseEntity.ok(payload);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','SECRETAIRE')")
    @PostMapping("/scan")
    public ResponseEntity<?> scanQr(@RequestBody @Valid ScanSeanceQrRequest request) {
        var seance = seanceUseCase.createFromQr(CenterId.of(request.centerId()), request.qrCode());

        var details = seanceUseCase.getDetails(CenterId.of(request.centerId()), seance.getId());
        var patient = details.patient();
        notificationService.notifySeanceCreated(
                request.centerId(),
                seance.getId(),
                seance.getPatientId(),
                patient.getNom(),
                patient.getPrenom(),
                seance.getDateSeance() == null ? null : seance.getDateSeance().toString()
        );

        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "dateSeance", seance.getDateSeance()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{seanceId}")
    public ResponseEntity<?> details(@PathVariable UUID seanceId,
                                     @RequestParam UUID centerId) {
        var details = seanceUseCase.getDetails(CenterId.of(centerId), seanceId);
        var seance = details.seance();
        var patient = details.patient();
        var voletParamedical = details.voletParamedical();
        var voletMedical = details.voletMedical();

        var seanceMap = new LinkedHashMap<String, Object>();
        seanceMap.put("id", seance.getId());
        seanceMap.put("centerId", seance.getCenterId());
        seanceMap.put("patientId", seance.getPatientId());
        seanceMap.put("dateSeance", seance.getDateSeance());
        seanceMap.put("status", seance.getStatus());
        seanceMap.put("createdAt", seance.getCreatedAt());
        seanceMap.put("validatedAt", seance.getValidatedAt());
        seanceMap.put("signedByInfirmierAt", seance.getSignedByInfirmierAt());
        seanceMap.put("signedByMedecinAt", seance.getSignedByMedecinAt());

        var patientMap = new LinkedHashMap<String, Object>();
        patientMap.put("id", patient.getId().value());
        patientMap.put("codePatient", patient.getCodePatient());
        patientMap.put("nom", patient.getNom());
        patientMap.put("prenom", patient.getPrenom());
        patientMap.put("sexe", patient.getSexe());
        patientMap.put("dateNaissance", patient.getDateNaissance());
        patientMap.put("numeroAssurance", patient.getNumeroAssurance() == null ? null : patient.getNumeroAssurance().value());
        patientMap.put("groupeSanguin", patient.getGroupeSanguin());
        patientMap.put("telMobile", patient.getTelMobile());
        patientMap.put("medecinTraitantId", patient.getMedecinTraitantId());
        patientMap.put("salleId", patient.getSalleId());
        patientMap.put("positionId", patient.getPositionId());

        Map<String, Object> paramedicalMap = null;
        if (voletParamedical != null) {
            paramedicalMap = new LinkedHashMap<>();
            paramedicalMap.put("poidsAvantKg", voletParamedical.getPoidsAvantKg());
            paramedicalMap.put("poidsApresKg", voletParamedical.getPoidsApresKg());
            paramedicalMap.put("taAvant", voletParamedical.getTaAvant());
            paramedicalMap.put("taApres", voletParamedical.getTaApres());
            paramedicalMap.put("dureeMinutes", voletParamedical.getDureeMinutes());
            paramedicalMap.put("debitSangMlMin", voletParamedical.getDebitSangMlMin());
            paramedicalMap.put("ultrafiltrationMl", voletParamedical.getUltrafiltrationMl());
            paramedicalMap.put("anticoagulant", voletParamedical.getAnticoagulant());
            paramedicalMap.put("typeDialysat", voletParamedical.getTypeDialysat());
            paramedicalMap.put("incidents", voletParamedical.getIncidents());
        }

        Map<String, Object> medicalMap = null;
        if (voletMedical != null) {
            medicalMap = new LinkedHashMap<>();
            medicalMap.put("prescription", voletMedical.getPrescription());
            medicalMap.put("toleranceSeance", voletMedical.getToleranceSeance());
            medicalMap.put("examenClinique", voletMedical.getExamenClinique());
            medicalMap.put("resultatsBiologiques", voletMedical.getResultatsBiologiques());
            medicalMap.put("ajustementsTherapeutiques", voletMedical.getAjustementsTherapeutiques());
            medicalMap.put("conclusionMedicale", voletMedical.getConclusionMedicale());
        }

        var consommables = Optional.ofNullable(jdbc.query(
                """
                        SELECT sm.article_id AS article_id,
                               a.code AS article_code,
                               a.libelle AS article_libelle,
                               a.unite AS article_unite,
                               sm.prix_unitaire AS valeur_unitaire,
                               SUM(sm.quantite) AS quantite,
                               SUM(COALESCE(sm.quantite, 0) * COALESCE(sm.prix_unitaire, 0)) AS total_valorise
                        FROM stock_movements sm
                        INNER JOIN articles a ON a.id = sm.article_id AND a.center_id = sm.center_id
                        WHERE sm.center_id = ?
                          AND sm.seance_id = ?
                          AND sm.mouvement_type = 'SORTIE'
                        GROUP BY sm.article_id, a.code, a.libelle, a.unite, sm.prix_unitaire
                        ORDER BY a.code
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("articleId", rs.getObject("article_id", UUID.class));
                    row.put("articleCode", rs.getString("article_code"));
                    row.put("articleLibelle", rs.getString("article_libelle"));
                    row.put("articleUnite", rs.getString("article_unite"));
                    row.put("quantite", rs.getBigDecimal("quantite"));
                    row.put("valeurUnitaire", rs.getBigDecimal("valeur_unitaire"));
                    row.put("totalValorise", rs.getBigDecimal("total_valorise"));
                    return row;
                },
                centerId,
                seance.getId()
        )).orElseGet(List::of);

        var totalValoriseConsommables = consommables.stream()
                .map(item -> (java.math.BigDecimal) item.getOrDefault("totalValorise", java.math.BigDecimal.ZERO))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> forfait = loadCurrentForfait(
                centerId,
                patient.getId().value(),
                seance.getDateSeance() != null ? seance.getDateSeance() : LocalDate.now()
        );

        var payload = new LinkedHashMap<String, Object>();
        payload.put("seance", seanceMap);
        payload.put("patient", patientMap);
        payload.put("paramedical", paramedicalMap);
        payload.put("medical", medicalMap);
        payload.put("consommables", consommables);
        payload.put("consommablesTotalValorise", totalValoriseConsommables);
        payload.put("forfait", forfait);
        return ResponseEntity.ok(payload);
    }

    private Map<String, Object> loadCurrentForfait(UUID centerId, UUID patientId, LocalDate referenceDate) {
        var activeRows = Optional.ofNullable(jdbc.query(
                """
                        SELECT f.id AS forfait_id,
                               f.code AS forfait_code,
                               f.libelle AS forfait_nom,
                               f.prix AS forfait_prix,
                               CAST(NULL AS INTEGER) AS forfait_nb_seances
                        FROM prise_en_charge p
                        INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id)
                                             AND f.center_id = p.center_id
                        WHERE p.center_id = ?
                          AND p.patient_id = ?
                          AND (
                                (p.date_debut_effectif IS NOT NULL AND p.date_fin_effectif IS NOT NULL AND ? BETWEEN p.date_debut_effectif AND p.date_fin_effectif)
                             OR (p.date_debut_demande IS NOT NULL AND p.date_fin_demande IS NOT NULL AND ? BETWEEN p.date_debut_demande AND p.date_fin_demande)
                          )
                        ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END,
                                 p.created_at DESC
                        """,
                this::mapForfaitRow,
                centerId,
                patientId,
                Date.valueOf(referenceDate),
                Date.valueOf(referenceDate)
        )).orElseGet(List::of);

        if (!activeRows.isEmpty()) {
            return activeRows.getFirst();
        }

        var fallbackRows = Optional.ofNullable(jdbc.query(
                """
                        SELECT f.id AS forfait_id,
                               f.code AS forfait_code,
                               f.libelle AS forfait_nom,
                               f.prix AS forfait_prix,
                               CAST(NULL AS INTEGER) AS forfait_nb_seances
                        FROM prise_en_charge p
                        INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id)
                                             AND f.center_id = p.center_id
                        WHERE p.center_id = ?
                          AND p.patient_id = ?
                          AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL
                        ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END,
                                 p.created_at DESC
                        """,
                this::mapForfaitRow,
                centerId,
                patientId
        )).orElseGet(List::of);

        return fallbackRows.isEmpty() ? null : fallbackRows.getFirst();
    }

    private Map<String, Object> mapForfaitRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getObject("forfait_id", UUID.class));
        row.put("code", rs.getString("forfait_code"));
        row.put("nom", rs.getString("forfait_nom"));
        row.put("prix", rs.getBigDecimal("forfait_prix"));
        row.put("nombreSeances", rs.getInt("forfait_nb_seances"));
        return row;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{seanceId}")
    public ResponseEntity<?> update(@PathVariable UUID seanceId, @RequestBody @Valid UpdateSeanceRequest request) {
        var seance = seanceUseCase.updateDate(CenterId.of(request.centerId()), seanceId, request.dateSeance());
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "dateSeance", seance.getDateSeance()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER')")
    @PostMapping("/{seanceId}/valider")
    public ResponseEntity<?> validate(@PathVariable UUID seanceId, @RequestBody @Valid ValidateSeanceRequest request) {
        var consommations = request.consommations() == null
                ? java.util.List.<SeanceArticleConsumption>of()
                : request.consommations().stream()
                .map(item -> new SeanceArticleConsumption(item.articleId(), item.quantite()))
                .toList();

        var seance = seanceUseCase.validate(CenterId.of(request.centerId()), seanceId, request.userId(), consommations);
        var details = seanceUseCase.getDetails(CenterId.of(request.centerId()), seance.getId());
        var patient = details.patient();
        notificationService.notifySeanceValidated(
                request.centerId(),
                seance.getId(),
                seance.getPatientId(),
                patient.getNom(),
                patient.getPrenom(),
                seance.getDateSeance() == null ? null : seance.getDateSeance().toString()
        );

        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "validatedAt", seance.getValidatedAt(),
                "signedByInfirmierAt", seance.getSignedByInfirmierAt()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/journal")
    public ResponseEntity<?> journalByDate(@RequestParam UUID centerId,
                                           @RequestParam LocalDate dateSeance) {
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

        var articles = jdbc.query(
                """
                        SELECT bsl.article_id AS article_id,
                               a.code AS article_code,
                               a.libelle AS article_libelle,
                               SUM(bsl.quantite) AS quantite_totale
                        FROM bons_sortie_lignes bsl
                        INNER JOIN bons_sortie bs ON bs.id = bsl.bon_sortie_id
                        INNER JOIN articles a ON a.id = bsl.article_id AND a.center_id = bs.center_id
                        WHERE bs.center_id = ?
                          AND bs.date_sortie = ?
                        GROUP BY bsl.article_id, a.code, a.libelle
                        ORDER BY a.code ASC
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("articleId", rs.getObject("article_id", UUID.class));
                    row.put("articleCode", rs.getString("article_code"));
                    row.put("articleLibelle", rs.getString("article_libelle"));
                    row.put("quantiteTotale", rs.getBigDecimal("quantite_totale"));
                    return row;
                },
                centerId,
                Date.valueOf(dateSeance)
        );

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
        return ResponseEntity.ok(buildSeanceDashboard(centerId, year, month));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard/details")
    public ResponseEntity<?> dashboardDetails(@RequestParam UUID centerId,
                                              @RequestParam int year,
                                              @RequestParam int month,
                                              @RequestParam String kind) {
        return ResponseEntity.ok(buildSeanceDashboardDetails(centerId, year, month, kind));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard/export")
    public ResponseEntity<?> exportDashboard(@RequestParam UUID centerId,
                                             @RequestParam int year,
                                             @RequestParam int month,
                                             @RequestParam(defaultValue = "csv") String format) {
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
        jdbc.update("DELETE FROM center_holiday WHERE id = ? AND center_id = ?", id, centerId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/calendar/closure")
    public ResponseEntity<?> addClosure(@RequestBody @Valid SeanceCalendarDayRequest request) {
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
        jdbc.update("DELETE FROM center_closure_day WHERE id = ? AND center_id = ?", id, centerId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private Map<String, Object> buildSeanceDashboard(UUID centerId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // Séances effectivement créées (présences)
        Long presenceCountRaw = jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND date_seance BETWEEN ? AND ?",
                Long.class,
                centerId,
                Date.valueOf(from),
                Date.valueOf(to)
        );
        long presenceCount = presenceCountRaw == null ? 0L : presenceCountRaw;

        // Jours non ouvrés centre (fériés + fermetures exceptionnelles)
        Set<LocalDate> blockedDates = new HashSet<>();
        blockedDates.addAll(loadBlockedDates("center_holiday", centerId, from, to));
        blockedDates.addAll(loadBlockedDates("center_closure_day", centerId, from, to));

        // Programmation patients (jours dialyse)
        List<Map<String, Object>> patients = jdbc.queryForList(
                """
                        SELECT id,
                               date_admission,
                               en_sommeil,
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
            if (effectiveStart.isAfter(to)) {
                continue;
            }
            consideredPatients++;
            for (LocalDate d = effectiveStart; !d.isAfter(to); d = d.plusDays(1)) {
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

        long absences = Math.max(0L, expectedSeances - presenceCount);

        // Répartition sexe + âge sur patients présents (distinct) durant le mois
        List<Map<String, Object>> presentPatients = jdbc.queryForList(
                """
                        SELECT DISTINCT p.id, p.sexe, p.date_naissance
                        FROM seances s
                        INNER JOIN patients p ON p.id = s.patient_id
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                        """,
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
        for (Map<String, Object> p : presentPatients) {
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
        payload.put("absenceDetails", Map.of(
                "formula", "absences = max(0, seancesPrevues - presences)",
                "periodStart", from,
                "periodEnd", to,
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

        Set<LocalDate> blockedDates = new HashSet<>();
        blockedDates.addAll(loadBlockedDates("center_holiday", centerId, from, to));
        blockedDates.addAll(loadBlockedDates("center_closure_day", centerId, from, to));

        List<Map<String, Object>> patients = jdbc.queryForList(
                """
                        SELECT id,
                               nom,
                               prenom,
                               date_admission,
                               en_sommeil,
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

        Map<String, String> presenceByKey = new HashMap<>();
        List<Map<String, Object>> presenceRows = jdbc.queryForList(
                """
                        SELECT s.patient_id AS patient_id,
                               s.date_seance AS date_seance,
                               s.statut AS statut
                        FROM seances s
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                        """,
                centerId,
                Date.valueOf(from),
                Date.valueOf(to)
        );
        for (Map<String, Object> row : presenceRows) {
            UUID patientId = (UUID) readColumn(row, "patient_id");
            LocalDate dateSeance = asLocalDate(readColumn(row, "date_seance"));
            if (patientId == null || dateSeance == null) continue;
            String key = patientId + "|" + dateSeance;
            presenceByKey.putIfAbsent(key, String.valueOf(readColumn(row, "statut")));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> patient : patients) {
            Boolean enSommeil = asBoolean(readColumn(patient, "en_sommeil"));
            if (Boolean.TRUE.equals(enSommeil)) continue;

            UUID patientId = (UUID) readColumn(patient, "id");
            String nom = Objects.toString(readColumn(patient, "nom"), "");
            String prenom = Objects.toString(readColumn(patient, "prenom"), "");
            LocalDate admission = asLocalDate(readColumn(patient, "date_admission"));
            LocalDate effectiveStart = admission == null || admission.isBefore(from) ? from : admission;
            if (effectiveStart.isAfter(to)) continue;

            for (LocalDate d = effectiveStart; !d.isAfter(to); d = d.plusDays(1)) {
                if (blockedDates.contains(d)) continue;
                if (!isPatientScheduledOn(patient, d)) continue;

                String key = patientId + "|" + d;
                boolean present = presenceByKey.containsKey(key);
                if (normalizedKind.equals("presence") != present) continue;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("patientId", patientId);
                row.put("patientNom", nom);
                row.put("patientPrenom", prenom);
                row.put("dateSeance", d);
                row.put("weekday", d.getDayOfWeek().name());
                row.put("scheduled", true);
                row.put("present", present);
                row.put("status", present ? presenceByKey.get(key) : "ABSENT");
                items.add(row);
            }
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

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/{seanceId}/signer-medecin")
    public ResponseEntity<?> signByMedecin(@PathVariable UUID seanceId,
                                           @RequestBody @Valid SignSeanceMedecinRequest request) {
        var seance = seanceUseCase.signByMedecin(CenterId.of(request.centerId()), seanceId, request.userId());
        return ResponseEntity.ok(Map.of(
                "id", seance.getId(),
                "status", seance.getStatus(),
                "signedByMedecinAt", seance.getSignedByMedecinAt()
        ));
    }
}



