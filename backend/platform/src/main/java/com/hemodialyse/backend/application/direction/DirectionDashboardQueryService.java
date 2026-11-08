package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.query.EffectifSql;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres.ChiffresDuCentre;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    /**
     * Part financière (CA, encaissé) : lecture publiée du module Facturation (slice F5).
     */
    private final SyntheseFinanciereDesCentres finance;
    /**
     * Séances réalisées des centres : lecture publiée de la Séance (SE7e, ADR 0099), mêmes requêtes qu'avant.
     */
    private final SeancesRealiseesDesCentresPubliees seancesRealisees;

    public DirectionDashboardQueryService(JdbcTemplate jdbc, SyntheseFinanciereDesCentres finance,
                                          SeancesRealiseesDesCentresPubliees seancesRealisees) {
        this.jdbc = jdbc;
        this.finance = finance;
        this.seancesRealisees = seancesRealisees;
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

        Map<UUID, Long> seances = new HashMap<>(seancesRealisees.parCentre(ids, start, end));

        // Factures émises sur la période et leurs règlements (encaissé à ce jour) : reste à recouvrer cohérent avec le CA.
        Map<UUID, ChiffresDuCentre> finances = finance.chiffresParCentre(ids, start, end);

        List<CentreStats> perCentre = new ArrayList<>();
        long totPatients = 0, totKt = 0, totSeances = 0, totFactures = 0;
        BigDecimal totHt = zero(), totTtc = zero(), totEnc = zero();
        for (CentreInfo c : societe.centres()) {
            long[] p = patients.getOrDefault(c.id(), new long[]{0, 0});
            long s = seances.getOrDefault(c.id(), 0L);
            ChiffresDuCentre f = finances.getOrDefault(c.id(), new ChiffresDuCentre(0, zero(), zero(), zero()));
            BigDecimal e = f.encaisse().signum() == 0 ? zero() : f.encaisse();
            perCentre.add(stats(c.id(), c.nom(), c.actif(), p[0], p[1], s, f.factures(), f.chiffreDAffairesHt(),
                    f.chiffreDAffairesTtc(), e));
            totPatients += p[0];
            totKt += p[1];
            totSeances += s;
            totFactures += f.factures();
            totHt = totHt.add(f.chiffreDAffairesHt());
            totTtc = totTtc.add(f.chiffreDAffairesTtc());
            totEnc = totEnc.add(e);
        }
        CentreStats totaux = stats(null, null, true, totPatients, totKt, totSeances, totFactures, totHt, totTtc, totEnc);
        PeriodComparison previous = previousPeriodTotals(ids, start, end);
        return new Overview(societeId, societe.raisonSociale(), start, end, OffsetDateTime.now(ZoneOffset.UTC),
                AnonymityPolicy.THRESHOLD, perCentre, totaux, monthly(ids, start, end), previous);
    }

    /**
     * Totaux de la même durée, immédiatement avant la période affichée (ex. période du 01 au 30/09 : comparaison du
     * 01 au 31/08 si le mois précédent a la même longueur, sinon des jours équivalents) — pour les deltas affichés à
     * côté des indicateurs financiers et d'activité. Le nombre de patients n'est volontairement pas comparé : c'est
     * un effectif constaté à l'instant présent, pas une donnée d'activité mesurée sur la période.
     */
    private PeriodComparison previousPeriodTotals(List<UUID> ids, LocalDate start, LocalDate end) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevTo = start.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        long seances = seancesRealisees.auTotal(ids, prevFrom, prevTo);
        ChiffresDuCentre cumul = finance.chiffresCumules(ids, prevFrom, prevTo);
        BigDecimal caHt = cumul.chiffreDAffairesHt();
        BigDecimal caTtc = cumul.chiffreDAffairesTtc();
        BigDecimal encaisse = cumul.encaisse();
        BigDecimal reste = caTtc.subtract(encaisse).max(BigDecimal.ZERO);
        BigDecimal taux = caTtc.signum() == 0 ? null
                : encaisse.multiply(BigDecimal.valueOf(100)).divide(caTtc, 1, RoundingMode.HALF_UP);
        return new PeriodComparison(prevFrom, prevTo, seances, caHt, caTtc, encaisse, reste, taux);
    }

    private List<MonthlyPoint> monthly(List<UUID> ids, LocalDate from, LocalDate to) {
        Map<String, MonthlyPoint> points = new LinkedHashMap<>();
        Map<String, long[]> seancesByKey = new HashMap<>();
        for (var mois : seancesRealisees.parMois(ids, from, to)) {
            seancesByKey.put(key(mois.centre(), mois.annee(), mois.mois()), new long[]{mois.nombre()});
        }
        Map<String, BigDecimal[]> caByKey = new HashMap<>();
        for (var mois : finance.chiffresParMois(ids, from, to)) {
            caByKey.put(key(mois.centre(), mois.annee(), mois.mois()),
                    new BigDecimal[]{mois.chiffreDAffairesHt(), mois.chiffreDAffairesTtc()});
        }
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
