package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.query.EffectifSql;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tableau de bord consolidé de la direction d'une société : indicateurs par centre et pour l'ensemble de la
 * société, sur une période.
 * <p>
 * <b>Anonymat</b> : seuls des agrégats (effectifs, sommes) sont calculés — aucune requête ne remonte une ligne
 * d'un patient — et tout effectif faible est masqué par {@link AnonymityPolicy}. Le périmètre est borné aux centres
 * de la société : chaque requête filtre sur {@code center_id IN (centres de la société)}.
 */
@Service
public class DirectionDashboardQueryService {

    /**
     * Amplitude maximale d'une période interrogée.
     */
    static final int MAX_YEARS = 5;
    private final JdbcTemplate jdbc;

    public DirectionDashboardQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Clé triable : {@code yyyy-MM|centre}.
     */
    private static String key(UUID centerId, int year, int month) {
        return String.format("%04d-%02d|%s", year, month, centerId);
    }

    static CentreStats stats(UUID centerId, String nom, boolean actif, long patients, long sousKt, long seances,
                             long factures, BigDecimal caHt, BigDecimal caTtc, BigDecimal encaisse) {
        BigDecimal reste = caTtc.subtract(encaisse).max(BigDecimal.ZERO);
        BigDecimal taux = caTtc.signum() == 0 ? null
                : encaisse.multiply(BigDecimal.valueOf(100)).divide(caTtc, 1, RoundingMode.HALF_UP);
        return new CentreStats(centerId, nom, actif, AnonymityPolicy.mask(patients), AnonymityPolicy.mask(sousKt),
                seances, factures, caHt, caTtc, encaisse, reste, taux);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static Object[] params(List<UUID> ids, Object... rest) {
        List<Object> all = new ArrayList<>(ids);
        all.addAll(List.of(rest));
        return all.toArray();
    }

    public SocieteInfo societe(UUID societeId) {
        String nom = jdbc.queryForObject("SELECT raison_sociale FROM societes WHERE id = ?", String.class, societeId);
        List<CentreInfo> centres = jdbc.query(
                "SELECT id, name, COALESCE(actif, TRUE) AS actif FROM centers WHERE societe_id = ? ORDER BY name",
                (rs, i) -> new CentreInfo(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("actif")),
                societeId);
        return new SocieteInfo(societeId, nom, centres);
    }

    public Overview overview(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : LocalDate.of(end.getYear(), 1, 1);
        if (start.isAfter(end)) {
            throw new BusinessException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessException("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + MAX_YEARS + " ans");
        }

        SocieteInfo societe = societe(societeId);
        List<UUID> ids = societe.centres().stream().map(CentreInfo::id).toList();
        if (ids.isEmpty()) {
            return new Overview(societeId, societe.raisonSociale(), start, end, OffsetDateTime.now(ZoneOffset.UTC),
                    AnonymityPolicy.THRESHOLD, List.of(), stats(null, null, true, 0, 0, 0, 0, zero(), zero(), zero()),
                    List.of(), null);
        }
        String in = placeholders(ids.size());
        Date sqlFrom = Date.valueOf(start);
        Date sqlTo = Date.valueOf(end);

        Map<UUID, long[]> patients = new HashMap<>();
        // Effectif de la période : un patient sorti avant le début n'est pas compté, un patient sorti pendant ou après l'est
        EffectifSql.Fragment effectif = EffectifSql.presentSur("p", start, end);
        List<Object> effectifArgs = new ArrayList<>(ids);
        effectifArgs.addAll(effectif.args());
        jdbc.query("SELECT p.center_id, COUNT(*) AS total, SUM(CASE WHEN p.sous_kt = TRUE THEN 1 ELSE 0 END) AS kt "
                        + "FROM patients p WHERE p.center_id IN (" + in + ") AND " + effectif.sql() + " GROUP BY p.center_id",
                rs -> {
                    patients.put(rs.getObject("center_id", UUID.class), new long[]{rs.getLong("total"), rs.getLong("kt")});
                }, effectifArgs.toArray());

        Map<UUID, Long> seances = new HashMap<>();
        jdbc.query("SELECT center_id, COUNT(*) AS n FROM seances WHERE center_id IN (" + in + ") "
                        + "AND date_seance BETWEEN ? AND ? AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id",
                rs -> {
                    seances.put(rs.getObject("center_id", UUID.class), rs.getLong("n"));
                }, params(ids, sqlFrom, sqlTo));

        Map<UUID, BigDecimal[]> factures = new HashMap<>(); // [count, ht, ttc]
        jdbc.query("SELECT center_id, COUNT(*) AS n, COALESCE(SUM(total_ht),0) AS ht, COALESCE(SUM(total_ttc),0) AS ttc "
                        + "FROM factures WHERE center_id IN (" + in + ") AND date_facturation BETWEEN ? AND ? GROUP BY center_id",
                rs -> {
                    factures.put(rs.getObject("center_id", UUID.class), new BigDecimal[]{
                            BigDecimal.valueOf(rs.getLong("n")), rs.getBigDecimal("ht"), rs.getBigDecimal("ttc")});
                }, params(ids, sqlFrom, sqlTo));

        // Règlements des factures émises sur la période (encaissé à ce jour) : reste à recouvrer cohérent avec le CA.
        Map<UUID, BigDecimal> encaisse = new HashMap<>();
        jdbc.query("SELECT f.center_id, COALESCE(SUM(r.montant),0) AS encaisse FROM facture_reglements r "
                        + "INNER JOIN factures f ON f.id = r.facture_id AND f.center_id = r.center_id "
                        + "WHERE f.center_id IN (" + in + ") AND f.date_facturation BETWEEN ? AND ? GROUP BY f.center_id",
                rs -> {
                    encaisse.put(rs.getObject("center_id", UUID.class), rs.getBigDecimal("encaisse"));
                }, params(ids, sqlFrom, sqlTo));

        List<CentreStats> perCentre = new ArrayList<>();
        long totPatients = 0, totKt = 0, totSeances = 0, totFactures = 0;
        BigDecimal totHt = zero(), totTtc = zero(), totEnc = zero();
        for (CentreInfo c : societe.centres()) {
            long[] p = patients.getOrDefault(c.id(), new long[]{0, 0});
            long s = seances.getOrDefault(c.id(), 0L);
            BigDecimal[] f = factures.getOrDefault(c.id(), new BigDecimal[]{BigDecimal.ZERO, zero(), zero()});
            BigDecimal e = encaisse.getOrDefault(c.id(), zero());
            perCentre.add(stats(c.id(), c.nom(), c.actif(), p[0], p[1], s, f[0].longValue(), f[1], f[2], e));
            totPatients += p[0];
            totKt += p[1];
            totSeances += s;
            totFactures += f[0].longValue();
            totHt = totHt.add(f[1]);
            totTtc = totTtc.add(f[2]);
            totEnc = totEnc.add(e);
        }
        CentreStats totaux = stats(null, null, true, totPatients, totKt, totSeances, totFactures, totHt, totTtc, totEnc);
        PeriodComparison previous = previousPeriodTotals(ids, in, start, end);
        return new Overview(societeId, societe.raisonSociale(), start, end, OffsetDateTime.now(ZoneOffset.UTC),
                AnonymityPolicy.THRESHOLD, perCentre, totaux, monthly(ids, in, sqlFrom, sqlTo), previous);
    }

    /**
     * Totaux de la même durée, immédiatement avant la période affichée (ex. période du 01 au 30/09 : comparaison du
     * 01 au 31/08 si le mois précédent a la même longueur, sinon des jours équivalents) — pour les deltas affichés à
     * côté des indicateurs financiers et d'activité. Le nombre de patients n'est volontairement pas comparé : c'est
     * un effectif constaté à l'instant présent, pas une donnée d'activité mesurée sur la période.
     */
    private PeriodComparison previousPeriodTotals(List<UUID> ids, String in, LocalDate start, LocalDate end) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevTo = start.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        Date sqlFrom = Date.valueOf(prevFrom);
        Date sqlTo = Date.valueOf(prevTo);

        Long seances = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id IN (" + in + ") "
                        + "AND date_seance BETWEEN ? AND ? AND statut IN ('VALIDEE','SIGNEE','FACTUREE')",
                Long.class, params(ids, sqlFrom, sqlTo));
        Map<String, Object> factures = jdbc.queryForMap("SELECT COALESCE(SUM(total_ht),0) AS ht, "
                + "COALESCE(SUM(total_ttc),0) AS ttc FROM factures WHERE center_id IN (" + in + ") "
                + "AND date_facturation BETWEEN ? AND ?", params(ids, sqlFrom, sqlTo));
        BigDecimal caHt = (BigDecimal) factures.get("ht");
        BigDecimal caTtc = (BigDecimal) factures.get("ttc");
        BigDecimal encaisse = jdbc.queryForObject("SELECT COALESCE(SUM(r.montant),0) FROM facture_reglements r "
                        + "INNER JOIN factures f ON f.id = r.facture_id AND f.center_id = r.center_id "
                        + "WHERE f.center_id IN (" + in + ") AND f.date_facturation BETWEEN ? AND ?",
                BigDecimal.class, params(ids, sqlFrom, sqlTo));
        BigDecimal reste = caTtc.subtract(encaisse).max(BigDecimal.ZERO);
        BigDecimal taux = caTtc.signum() == 0 ? null
                : encaisse.multiply(BigDecimal.valueOf(100)).divide(caTtc, 1, RoundingMode.HALF_UP);
        return new PeriodComparison(prevFrom, prevTo, seances == null ? 0 : seances, caHt, caTtc, encaisse, reste, taux);
    }

    private List<MonthlyPoint> monthly(List<UUID> ids, String in, Date from, Date to) {
        Map<String, MonthlyPoint> points = new LinkedHashMap<>();
        Map<String, long[]> seancesByKey = new HashMap<>();
        jdbc.query("SELECT center_id, EXTRACT(YEAR FROM date_seance) AS y, EXTRACT(MONTH FROM date_seance) AS m, COUNT(*) AS n "
                        + "FROM seances WHERE center_id IN (" + in + ") AND date_seance BETWEEN ? AND ? "
                        + "AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id, y, m",
                rs -> {
                    seancesByKey.put(key(rs.getObject("center_id", UUID.class), rs.getInt("y"), rs.getInt("m")),
                            new long[]{rs.getLong("n")});
                }, params(ids, from, to));
        Map<String, BigDecimal[]> caByKey = new HashMap<>();
        jdbc.query("SELECT center_id, EXTRACT(YEAR FROM date_facturation) AS y, EXTRACT(MONTH FROM date_facturation) AS m, "
                        + "COALESCE(SUM(total_ht),0) AS ht, COALESCE(SUM(total_ttc),0) AS ttc "
                        + "FROM factures WHERE center_id IN (" + in + ") AND date_facturation BETWEEN ? AND ? "
                        + "GROUP BY center_id, y, m",
                rs -> {
                    caByKey.put(key(rs.getObject("center_id", UUID.class), rs.getInt("y"), rs.getInt("m")),
                            new BigDecimal[]{rs.getBigDecimal("ht"), rs.getBigDecimal("ttc")});
                }, params(ids, from, to));
        java.util.Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(seancesByKey.keySet());
        keys.addAll(caByKey.keySet());
        for (String k : keys) {
            String[] parts = k.split("\\|");
            long n = seancesByKey.containsKey(k) ? seancesByKey.get(k)[0] : 0;
            BigDecimal[] ca = caByKey.getOrDefault(k, new BigDecimal[]{zero(), zero()});
            points.put(k, new MonthlyPoint(parts[0], UUID.fromString(parts[1]), n, ca[0], ca[1]));
        }
        return new ArrayList<>(points.values());
    }

    public record SocieteInfo(UUID id, String raisonSociale, List<CentreInfo> centres) {
    }

    public record CentreInfo(UUID id, String nom, boolean actif) {
    }

    /**
     * Indicateurs d'un centre (ou totaux de la société quand {@code centerId} est {@code null}).
     */
    public record CentreStats(UUID centerId, String nom, boolean actif,
                              Long patients, Long patientsSousKt,
                              long seances,
                              long factures, BigDecimal caHt, BigDecimal caTtc,
                              BigDecimal encaisse, BigDecimal resteARecouvrer, BigDecimal tauxEncaissement) {
    }

    public record MonthlyPoint(String mois, UUID centerId, long seances, BigDecimal caHt, BigDecimal caTtc) {
    }

    /**
     * Totaux de la période de même durée précédant immédiatement la période affichée, pour calculer un delta
     * (activité et finances uniquement : le nombre de patients n'est pas une donnée d'activité sur la période).
     */
    public record PeriodComparison(LocalDate from, LocalDate to, long seances, BigDecimal caHt, BigDecimal caTtc,
                                   BigDecimal encaisse, BigDecimal resteARecouvrer, BigDecimal tauxEncaissement) {
    }

    public record Overview(UUID societeId, String societeNom, LocalDate from, LocalDate to,
                           OffsetDateTime generatedAt, int seuilAnonymat,
                           List<CentreStats> centres, CentreStats totaux, List<MonthlyPoint> mensuel,
                           PeriodComparison periodePrecedente) {
    }
}
