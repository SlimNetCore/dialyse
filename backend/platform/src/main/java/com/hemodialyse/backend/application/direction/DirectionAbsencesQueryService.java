package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Absences des patients sur une période, pour la direction d'une société : par centre et consolidées — nombre,
 * motifs, valorisation (forfait de la prise en charge converti en HT), taux d'absentéisme et part du chiffre
 * d'affaires HT facturé. Agrégats uniquement (aucune donnée patient) ; un effectif faible est masqué
 * ({@link AnonymityPolicy}), ainsi que la valorisation qui permettrait de le déduire.
 */
@Service
public class DirectionAbsencesQueryService {

    static final String NON_QUALIFIE = "NON_QUALIFIE";
    private static final int MAX_YEARS = 5;
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;
    private final DirectionDashboardQueryService dashboard;

    public DirectionAbsencesQueryService(JdbcTemplate jdbc, DirectionDashboardQueryService dashboard) {
        this.jdbc = jdbc;
        this.dashboard = dashboard;
    }

    private static List<MotifAbsenceStat> motifs(Map<String, Cumul> motifs) {
        return motifs.entrySet().stream()
                .map(e -> {
                    Cumul c = e.getValue();
                    Long nb = AnonymityPolicy.mask(c.comptabilisees());
                    return new MotifAbsenceStat(e.getKey(), nb, nb == null ? null : c.valorisationHt());
                })
                .sorted(Comparator.comparing((MotifAbsenceStat m) -> m.nb() == null ? -1L : m.nb()).reversed()
                        .thenComparing(MotifAbsenceStat::motif))
                .toList();
    }

    private static AbsencesStats stats(Cumul c, long seances, BigDecimal caHt) {
        long comptabilisees = c.comptabilisees();
        BigDecimal taux = pourcentage(BigDecimal.valueOf(comptabilisees),
                BigDecimal.valueOf(comptabilisees + seances));
        BigDecimal partCa = pourcentage(c.valorisationHt(), caHt);
        return new AbsencesStats(comptabilisees, c.justifiees, c.nonJustifiees, c.aQualifier, c.rattrapees, seances,
                c.valorisationHt(), c.valorisationTtc(), caHt, taux, partCa);
    }

    private static BigDecimal pourcentage(BigDecimal numerateur, BigDecimal denominateur) {
        if (denominateur == null || denominateur.signum() == 0) return null;
        return numerateur.multiply(CENT).divide(denominateur, 2, RoundingMode.HALF_UP);
    }

    private static Object[] parametres(Collection<UUID> ids, LocalDate from, LocalDate to) {
        List<Object> p = new ArrayList<>(ids);
        p.add(Date.valueOf(from));
        p.add(Date.valueOf(to));
        return p.toArray();
    }

    public AbsencesOverview absences(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : LocalDate.of(end.getYear(), 1, 1);
        if (start.isAfter(end)) {
            throw new BusinessException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessException("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + MAX_YEARS + " ans");
        }
        List<CentreInfo> centres = dashboard.societe(societeId).centres();
        if (centres.isEmpty()) {
            return new AbsencesOverview(societeId, start, end, OffsetDateTime.now(ZoneOffset.UTC),
                    stats(new Cumul(), 0, BigDecimal.ZERO), List.of(), List.of(), List.of());
        }
        List<UUID> ids = centres.stream().map(CentreInfo::id).toList();
        String in = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] params = parametres(ids, start, end);

        Map<UUID, Long> seances = new HashMap<>();
        jdbc.query("SELECT center_id, COUNT(*) AS n FROM seances WHERE center_id IN (" + in
                        + ") AND date_seance BETWEEN ? AND ? AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id",
                rs -> {
                    seances.put(rs.getObject("center_id", UUID.class), rs.getLong("n"));
                }, params);
        Map<UUID, BigDecimal> ca = new HashMap<>();
        jdbc.query("SELECT center_id, COALESCE(SUM(total_ht),0) AS ht FROM factures WHERE center_id IN (" + in
                        + ") AND date_facturation BETWEEN ? AND ? GROUP BY center_id",
                rs -> {
                    ca.put(rs.getObject("center_id", UUID.class), rs.getBigDecimal("ht"));
                }, params);

        Map<UUID, Cumul> parCentre = new HashMap<>();
        Map<UUID, Map<String, Cumul>> motifsParCentre = new HashMap<>();
        jdbc.query("SELECT center_id, statut, motif, COUNT(*) AS n, COALESCE(SUM(montant_ht),0) AS ht, "
                        + "COALESCE(SUM(prix_ttc),0) AS ttc FROM absence_patient WHERE center_id IN (" + in
                        + ") AND date_seance BETWEEN ? AND ? AND statut <> 'ANNULEE' GROUP BY center_id, statut, motif",
                rs -> {
                    UUID centre = rs.getObject("center_id", UUID.class);
                    String statut = rs.getString("statut");
                    long n = rs.getLong("n");
                    BigDecimal ht = rs.getBigDecimal("ht");
                    BigDecimal ttc = rs.getBigDecimal("ttc");
                    Cumul c = parCentre.computeIfAbsent(centre, k -> new Cumul());
                    c.ajouterStatut(statut, n, ht, ttc);
                    if (!"RATTRAPEE".equals(statut)) {
                        String motif = rs.getString("motif") == null ? NON_QUALIFIE : rs.getString("motif");
                        motifsParCentre.computeIfAbsent(centre, k -> new LinkedHashMap<>())
                                .computeIfAbsent(motif, k -> new Cumul()).ajouterStatut(statut, n, ht, ttc);
                    }
                }, params);

        List<CentreAbsences> lignes = new ArrayList<>();
        Cumul total = new Cumul();
        Map<String, Cumul> motifsTotal = new TreeMap<>();
        long seancesTotal = 0;
        BigDecimal caTotal = BigDecimal.ZERO;
        for (CentreInfo centre : centres) {
            Cumul c = parCentre.getOrDefault(centre.id(), new Cumul());
            long nbSeances = seances.getOrDefault(centre.id(), 0L);
            BigDecimal caHt = ca.getOrDefault(centre.id(), BigDecimal.ZERO);
            Map<String, Cumul> motifs = motifsParCentre.getOrDefault(centre.id(), Map.of());
            lignes.add(new CentreAbsences(centre.id(), centre.nom(), centre.actif(), stats(c, nbSeances, caHt),
                    motifs(motifs)));
            total.ajouter(c);
            motifs.forEach((m, v) -> motifsTotal.computeIfAbsent(m, k -> new Cumul()).ajouter(v));
            seancesTotal += nbSeances;
            caTotal = caTotal.add(caHt);
        }
        return new AbsencesOverview(societeId, start, end, OffsetDateTime.now(ZoneOffset.UTC),
                stats(total, seancesTotal, caTotal), lignes, motifs(motifsTotal), mensuel(in, params));
    }

    private List<MoisAbsences> mensuel(String in, Object[] params) {
        List<MoisAbsences> mois = new ArrayList<>();
        jdbc.query("SELECT EXTRACT(YEAR FROM date_seance) AS y, EXTRACT(MONTH FROM date_seance) AS m, COUNT(*) AS n, "
                        + "COALESCE(SUM(montant_ht),0) AS ht FROM absence_patient WHERE center_id IN (" + in
                        + ") AND date_seance BETWEEN ? AND ? AND statut IN ('A_QUALIFIER','JUSTIFIEE','NON_JUSTIFIEE') "
                        + "GROUP BY y, m ORDER BY y, m",
                rs -> {
                    long n = rs.getLong("n");
                    Long masque = AnonymityPolicy.mask(n);
                    mois.add(new MoisAbsences(rs.getInt("y"), rs.getInt("m"), masque,
                            masque == null ? null : rs.getBigDecimal("ht")));
                }, params);
        return mois;
    }

    /**
     * Cumul des absences par statut (hors annulées) : seules les absences non rattrapées sont valorisées.
     */
    private static final class Cumul {
        private long justifiees;
        private long nonJustifiees;
        private long aQualifier;
        private long rattrapees;
        private BigDecimal ht = BigDecimal.ZERO;
        private BigDecimal ttc = BigDecimal.ZERO;

        void ajouterStatut(String statut, long n, BigDecimal montantHt, BigDecimal montantTtc) {
            switch (statut) {
                case "JUSTIFIEE" -> justifiees += n;
                case "NON_JUSTIFIEE" -> nonJustifiees += n;
                case "A_QUALIFIER" -> aQualifier += n;
                default -> {
                    rattrapees += n;
                    return;
                }
            }
            ht = ht.add(montantHt);
            ttc = ttc.add(montantTtc);
        }

        void ajouter(Cumul autre) {
            justifiees += autre.justifiees;
            nonJustifiees += autre.nonJustifiees;
            aQualifier += autre.aQualifier;
            rattrapees += autre.rattrapees;
            ht = ht.add(autre.ht);
            ttc = ttc.add(autre.ttc);
        }

        long comptabilisees() {
            return justifiees + nonJustifiees + aQualifier;
        }

        BigDecimal valorisationHt() {
            return ht;
        }

        BigDecimal valorisationTtc() {
            return ttc;
        }
    }

    /**
     * Indicateurs d'absence d'un centre (ou totaux). {@code nbAbsences} = absences comptabilisées (non rattrapées, non
     * annulées) ; {@code tauxAbsenteisme} = absences / (absences + séances réalisées), en % ; {@code partCaHt} =
     * valorisation HT / chiffre d'affaires HT facturé de la période, en % (null si pas de CA).
     */
    public record AbsencesStats(long nbAbsences, long nbJustifiees, long nbNonJustifiees, long nbAQualifier,
                                long nbRattrapees, long nbSeances, BigDecimal valorisationHt,
                                BigDecimal valorisationTtc, BigDecimal caHt, BigDecimal tauxAbsenteisme,
                                BigDecimal partCaHt) {
    }

    /**
     * Effectif et valorisation HT d'un motif ({@code NON_QUALIFIE} pour les absences en attente de motif) ; masqués
     * ({@code null}) si l'effectif est trop faible.
     */
    public record MotifAbsenceStat(String motif, Long nb, BigDecimal valorisationHt) {
    }

    public record CentreAbsences(UUID centerId, String nom, boolean actif, AbsencesStats stats,
                                 List<MotifAbsenceStat> motifs) {
    }

    public record MoisAbsences(int annee, int mois, Long nbAbsences, BigDecimal valorisationHt) {
    }

    public record AbsencesOverview(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt,
                                   AbsencesStats total, List<CentreAbsences> centres, List<MotifAbsenceStat> motifs,
                                   List<MoisAbsences> mensuel) {
    }
}
