package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.SocieteInfo;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Répartitions détaillées du tableau de bord de la direction, par centre : patients par sexe et par tranche d'âge,
 * patients / séances / chiffre d'affaires HT par caisse d'assurance, et traitement de l'anémie (EPO, fer).
 * <p>
 * Agrégats anonymes uniquement : tout effectif de patients inférieur au seuil d'anonymat ({@link AnonymityPolicy})
 * est masqué ({@code null}). Requêtes bornées par les centres de la société.
 */
@Service
public class DirectionBreakdownQueryService {

    public static final String CAISSE_INCONNUE = "";
    static final List<String> AGE_CODES = List.of("0_17", "18_29", "30_44", "45_59", "60_PLUS", "INCONNU");
    private static final int MAX_YEARS = 5;
    private static final String CAISSE_JOIN =
            "LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id "
                    + "LEFT JOIN agence ag ON ag.id = cp.agence_id "
                    + "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id ";
    private final JdbcTemplate jdbc;
    private final DirectionDashboardQueryService dashboard;

    public DirectionBreakdownQueryService(JdbcTemplate jdbc, DirectionDashboardQueryService dashboard) {
        this.jdbc = jdbc;
        this.dashboard = dashboard;
    }

    /**
     * Tranche d'âge d'un patient à la date donnée ({@code INCONNU} sans date de naissance ou date future).
     */
    static String ageBucket(LocalDate birth, LocalDate today) {
        if (birth == null || birth.isAfter(today)) return "INCONNU";
        int age = Period.between(birth, today).getYears();
        if (age < 18) return "0_17";
        if (age < 30) return "18_29";
        if (age < 45) return "30_44";
        if (age < 60) return "45_59";
        return "60_PLUS";
    }

    private static Agg cell(Map<String, Agg> cells, UUID center, String code, String nom) {
        Agg a = cells.computeIfAbsent(center + "|" + code, k -> new Agg(center, code, nom));
        if (a.nom == null && nom != null) a.nom = nom;
        return a;
    }

    public Breakdown breakdown(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : LocalDate.of(end.getYear(), 1, 1);
        if (start.isAfter(end)) {
            throw new BusinessException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessException("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + MAX_YEARS + " ans");
        }
        SocieteInfo societe = dashboard.societe(societeId);
        List<CentreInfo> centres = societe.centres();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (centres.isEmpty()) {
            return new Breakdown(societeId, start, end, now, AnonymityPolicy.THRESHOLD, List.of(), List.of(), List.of(),
                    List.of(), List.of());
        }
        List<UUID> ids = centres.stream().map(CentreInfo::id).toList();
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        Date sqlFrom = Date.valueOf(start);
        Date sqlTo = Date.valueOf(end);

        Map<UUID, long[]> sexes = new HashMap<>();          // [M, F, autre]
        Map<UUID, Map<String, Long>> ages = new HashMap<>();
        loadPatientProfiles(in, ids, sexes, ages);

        Map<String, Agg> caisseCells = new LinkedHashMap<>();
        loadCaissePatients(in, ids, caisseCells);
        loadCaisseSeances(in, ids, sqlFrom, sqlTo, caisseCells);
        loadCaisseCa(in, ids, sqlFrom, sqlTo, caisseCells);

        Map<UUID, Anemie> anemie = new HashMap<>();
        loadAnemie(in, ids, sqlFrom, sqlTo, anemie);

        Map<UUID, String> names = new HashMap<>();
        centres.forEach(c -> names.put(c.id(), c.nom()));

        List<SexeRow> sexeRows = new ArrayList<>();
        List<AgeRow> ageRows = new ArrayList<>();
        List<AnemieRow> anemieRows = new ArrayList<>();
        for (CentreInfo c : centres) {
            long[] s = sexes.getOrDefault(c.id(), new long[3]);
            sexeRows.add(new SexeRow(c.id(), c.nom(), AnonymityPolicy.mask(s[0]), AnonymityPolicy.mask(s[1]),
                    AnonymityPolicy.mask(s[2])));
            Map<String, Long> a = ages.getOrDefault(c.id(), Map.of());
            List<AgeBucket> buckets = new ArrayList<>();
            for (String code : AGE_CODES) {
                buckets.add(new AgeBucket(code, AnonymityPolicy.mask(a.getOrDefault(code, 0L))));
            }
            ageRows.add(new AgeRow(c.id(), c.nom(), buckets));
            anemieRows.add(anemie.getOrDefault(c.id(), new Anemie()).toRow(c.id(), c.nom()));
        }

        List<CaisseRow> caisseRows = new ArrayList<>();
        Map<String, Agg> totals = new LinkedHashMap<>();
        for (Agg cell : caisseCells.values()) {
            caisseRows.add(new CaisseRow(cell.centerId, names.get(cell.centerId), cell.code, cell.nom,
                    AnonymityPolicy.mask(cell.patients), cell.seances, cell.caHt));
            Agg t = totals.computeIfAbsent(cell.code, k -> new Agg(null, cell.code, cell.nom));
            t.patients += cell.patients;
            t.seances += cell.seances;
            t.caHt = t.caHt.add(cell.caHt);
        }
        caisseRows.sort((x, y) -> {
            int byCentre = String.valueOf(x.centre()).compareToIgnoreCase(String.valueOf(y.centre()));
            return byCentre != 0 ? byCentre : y.caHt().compareTo(x.caHt());
        });
        List<CaisseTotal> caisseTotaux = totals.values().stream()
                .map(t -> new CaisseTotal(t.code, t.nom, AnonymityPolicy.mask(t.patients), t.seances, t.caHt))
                .sorted((x, y) -> y.caHt().compareTo(x.caHt())).toList();

        return new Breakdown(societeId, start, end, now, AnonymityPolicy.THRESHOLD, sexeRows, ageRows, caisseRows,
                caisseTotaux, anemieRows);
    }

    private void loadPatientProfiles(String in, List<UUID> ids, Map<UUID, long[]> sexes,
                                     Map<UUID, Map<String, Long>> ages) {
        LocalDate today = LocalDate.now();
        jdbc.query("SELECT center_id, sexe, date_naissance FROM patients WHERE center_id IN (" + in + ")", rs -> {
            UUID center = rs.getObject("center_id", UUID.class);
            long[] s = sexes.computeIfAbsent(center, k -> new long[3]);
            String sexe = rs.getString("sexe");
            String initial = sexe == null ? "" : sexe.trim().toUpperCase(Locale.ROOT);
            s[initial.startsWith("M") ? 0 : initial.startsWith("F") ? 1 : 2]++;
            java.sql.Date birth = rs.getDate("date_naissance");
            ages.computeIfAbsent(center, k -> new HashMap<>())
                    .merge(ageBucket(birth == null ? null : birth.toLocalDate(), today), 1L, Long::sum);
        }, ids.toArray());
    }

    private void loadCaissePatients(String in, List<UUID> ids, Map<String, Agg> cells) {
        jdbc.query("SELECT p.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COUNT(*) AS n "
                        + "FROM patients p " + CAISSE_JOIN + "WHERE p.center_id IN (" + in + ") "
                        + "GROUP BY p.center_id, COALESCE(ca.code, '')",
                rs -> {
                    cell(cells, rs.getObject("center_id", UUID.class), rs.getString("code"), rs.getString("nom"))
                            .patients += rs.getLong("n");
                }, ids.toArray());
    }

    private void loadCaisseSeances(String in, List<UUID> ids, Date from, Date to, Map<String, Agg> cells) {
        List<Object> args = new ArrayList<>(ids);
        args.addAll(List.of(from, to));
        jdbc.query("SELECT s.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COUNT(*) AS n "
                        + "FROM seances s INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id "
                        + CAISSE_JOIN + "WHERE s.center_id IN (" + in + ") AND s.date_seance BETWEEN ? AND ? "
                        + "AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, COALESCE(ca.code, '')",
                rs -> {
                    cell(cells, rs.getObject("center_id", UUID.class), rs.getString("code"), rs.getString("nom"))
                            .seances += rs.getLong("n");
                }, args.toArray());
    }

    /**
     * CA HT des factures de la période, par caisse (agence figée sur la facture au moment de sa création).
     */
    private void loadCaisseCa(String in, List<UUID> ids, Date from, Date to, Map<String, Agg> cells) {
        List<Object> args = new ArrayList<>(ids);
        args.addAll(List.of(from, to));
        jdbc.query("SELECT f.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, "
                        + "COALESCE(SUM(f.total_ht), 0) AS ht FROM factures f "
                        + "LEFT JOIN agence ag ON ag.id = f.agence_id_snapshot "
                        + "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id "
                        + "WHERE f.center_id IN (" + in + ") AND f.date_facturation BETWEEN ? AND ? "
                        + "GROUP BY f.center_id, COALESCE(ca.code, '')",
                rs -> {
                    Agg a = cell(cells, rs.getObject("center_id", UUID.class), rs.getString("code"), rs.getString("nom"));
                    a.caHt = a.caHt.add(rs.getBigDecimal("ht"));
                }, args.toArray());
    }

    // ───────────────────────────── Chargement ─────────────────────────────

    private void loadAnemie(String in, List<UUID> ids, Date from, Date to, Map<UUID, Anemie> anemie) {
        List<Object> args = new ArrayList<>(ids);
        args.addAll(List.of(from, to));
        jdbc.query("SELECT center_id, "
                        + "COUNT(DISTINCT CASE WHEN administree = TRUE AND type_traitement = 'EPO' THEN patient_id END) AS pat_epo, "
                        + "COUNT(DISTINCT CASE WHEN administree = TRUE AND type_traitement = 'FER_INJECTABLE' THEN patient_id END) AS pat_fer, "
                        + "SUM(CASE WHEN administree = TRUE AND type_traitement = 'EPO' THEN 1 ELSE 0 END) AS adm_epo, "
                        + "SUM(CASE WHEN administree = TRUE AND type_traitement = 'FER_INJECTABLE' THEN 1 ELSE 0 END) AS adm_fer, "
                        + "SUM(CASE WHEN administree = FALSE THEN 1 ELSE 0 END) AS non_adm "
                        + "FROM administrations_anemie WHERE center_id IN (" + in + ") "
                        + "AND date_administration BETWEEN ? AND ? GROUP BY center_id",
                rs -> {
                    Anemie a = anemie.computeIfAbsent(rs.getObject("center_id", UUID.class), k -> new Anemie());
                    a.patientsEpo = rs.getLong("pat_epo");
                    a.patientsFer = rs.getLong("pat_fer");
                    a.admEpo = rs.getLong("adm_epo");
                    a.admFer = rs.getLong("adm_fer");
                    a.nonAdm = rs.getLong("non_adm");
                }, args.toArray());
        jdbc.query("SELECT center_id, "
                        + "SUM(CASE WHEN epo_enabled = TRUE THEN 1 ELSE 0 END) AS sous_epo, "
                        + "SUM(CASE WHEN fer_enabled = TRUE THEN 1 ELSE 0 END) AS sous_fer "
                        + "FROM patients WHERE center_id IN (" + in + ") GROUP BY center_id",
                rs -> {
                    Anemie a = anemie.computeIfAbsent(rs.getObject("center_id", UUID.class), k -> new Anemie());
                    a.sousEpo = rs.getLong("sous_epo");
                    a.sousFer = rs.getLong("sous_fer");
                }, ids.toArray());
    }

    public record SexeRow(UUID centerId, String nom, Long masculin, Long feminin, Long autre) {
    }

    public record AgeBucket(String code, Long count) {
    }

    public record AgeRow(UUID centerId, String nom, List<AgeBucket> tranches) {
    }

    /**
     * {@code caisseCode} vide : patients sans caisse renseignée.
     */
    public record CaisseRow(UUID centerId, String centre, String caisseCode, String caisse, Long patients,
                            long seances, BigDecimal caHt) {
    }

    public record CaisseTotal(String caisseCode, String caisse, Long patients, long seances, BigDecimal caHt) {
    }

    public record AnemieRow(UUID centerId, String nom, Long patientsEpo, Long patientsFer,
                            long administreesEpo, long administreesFer, long nonAdministrees,
                            BigDecimal tauxAdministration, Long patientsSousEpo, Long patientsSousFer) {
    }

    public record Breakdown(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt,
                            int seuilAnonymat, List<SexeRow> sexe, List<AgeRow> ages, List<CaisseRow> caisses,
                            List<CaisseTotal> caisseTotaux, List<AnemieRow> anemie) {
    }

    // ───────────────────────────── Agrégats internes ─────────────────────────────

    private static final class Agg {
        final UUID centerId;
        final String code;
        String nom;
        long patients;
        long seances;
        BigDecimal caHt = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Agg(UUID centerId, String code, String nom) {
            this.centerId = centerId;
            this.code = code;
            this.nom = nom;
        }
    }

    private static final class Anemie {
        long patientsEpo, patientsFer, admEpo, admFer, nonAdm, sousEpo, sousFer;

        AnemieRow toRow(UUID centerId, String nom) {
            long total = admEpo + admFer + nonAdm;
            BigDecimal taux = total == 0 ? null
                    : BigDecimal.valueOf((admEpo + admFer) * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
            return new AnemieRow(centerId, nom, AnonymityPolicy.mask(patientsEpo), AnonymityPolicy.mask(patientsFer),
                    admEpo, admFer, nonAdm, taux, AnonymityPolicy.mask(sousEpo), AnonymityPolicy.mask(sousFer));
        }
    }
}
