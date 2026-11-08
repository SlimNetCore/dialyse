package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres.ChiffresDuCentre;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final DirectionDashboardQueryService dashboard;

    private final SyntheseFinanciereDesCentres finance;
    private final AbsencesDuPilotage pilotage;
    private final SeancesRealiseesDesCentresPubliees seancesRealisees;

    public DirectionAbsencesQueryService(DirectionDashboardQueryService dashboard, SyntheseFinanciereDesCentres finance,
                                         AbsencesDuPilotage pilotage, SeancesRealiseesDesCentresPubliees seancesRealisees) {
        this.finance = finance;
        this.pilotage = pilotage;
        this.dashboard = dashboard;
        this.seancesRealisees = seancesRealisees;
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
        // KB-ABS-07 : un effectif de 1 à 4 absences comptabilisées est masqué (null) dans TOUS les chiffres qui en
        // dépendent (effectifs par statut, valorisation, taux, part du CA), sinon le masquage des motifs et du mensuel
        // serait contournable par les stats. Les rattrapées ont leur propre seuil.
        boolean masque = AnonymityPolicy.mask(comptabilisees) == null;
        BigDecimal taux = masque ? null : pourcentage(BigDecimal.valueOf(comptabilisees),
                BigDecimal.valueOf(comptabilisees + seances));
        BigDecimal partCa = masque ? null : pourcentage(c.valorisationHt(), caHt);
        // chaque effectif par statut a son propre seuil : un statut de 1 à 4 absences est masqué même si le centre est publié
        return new AbsencesStats(AnonymityPolicy.mask(comptabilisees),
                masque ? null : AnonymityPolicy.mask(c.justifiees),
                masque ? null : AnonymityPolicy.mask(c.nonJustifiees),
                masque ? null : AnonymityPolicy.mask(c.aQualifier), AnonymityPolicy.mask(c.rattrapees),
                seances, masque ? null : c.valorisationHt(), masque ? null : c.valorisationTtc(), caHt, taux, partCa);
    }

    private static BigDecimal pourcentage(BigDecimal numerateur, BigDecimal denominateur) {
        if (denominateur == null || denominateur.signum() == 0) return null;
        return numerateur.multiply(CENT).divide(denominateur, 2, RoundingMode.HALF_UP);
    }

    public AbsencesOverview absences(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate aujourdhui = pilotage.aujourdhui();
        LocalDate end = to != null ? to : aujourdhui;
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
                    stats(new Cumul(), 0, BigDecimal.ZERO), List.of(), List.of(), List.of(), 0);
        }
        List<UUID> ids = centres.stream().map(CentreInfo::id).toList();

        // Dénominateur du taux d'absentéisme : lecture publiée de la Séance (SE7e, ADR 0099), même requête que la vue d'ensemble
        Map<UUID, Long> seances = new HashMap<>(seancesRealisees.parCentre(ids, start, end));
        Map<UUID, BigDecimal> ca = new HashMap<>();
        finance.chiffresParCentre(ids, start, end).forEach((centre, chiffres) -> ca.put(centre, chiffres.chiffreDAffairesHt()));

        // KB-ABS-35 étendu à la Direction : aucune absence postérieure à aujourd'hui (fuseau du centre) n'est comptée, quel que soit `to`
        LocalDate finDesAbsences = end.isAfter(aujourdhui) ? aujourdhui : end;
        Map<UUID, Cumul> parCentre = new HashMap<>();
        Map<UUID, Map<String, Cumul>> motifsParCentre = new HashMap<>();
        for (AbsencesDuPilotage.CumulDuCentre g : finDesAbsences.isBefore(start) ? List.<AbsencesDuPilotage.CumulDuCentre>of()
                : pilotage.cumulParCentre(ids, start, finDesAbsences)) {
            Cumul c = parCentre.computeIfAbsent(g.centre(), k -> new Cumul());
            c.ajouterStatut(g.statut(), g.nombre(), g.montantHt(), g.montantTtc());
            if (!"RATTRAPEE".equals(g.statut())) {
                String motif = g.motif() == null ? NON_QUALIFIE : g.motif();
                motifsParCentre.computeIfAbsent(g.centre(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(motif, k -> new Cumul()).ajouterStatut(g.statut(), g.nombre(), g.montantHt(), g.montantTtc());
            }
        }

        // KB-ABS-07 (décision du 2026-10-22 : « masquer par centre seulement ») : un centre de 1 à 4 absences comptabilisées
        // est masqué ET exclu du total, des motifs consolidés et du mensuel, qui ne portent que sur les centres publiés ;
        // sinon la différence total - centres publiés redonnerait le centre masqué.
        List<CentreAbsences> lignes = new ArrayList<>();
        Cumul total = new Cumul();
        Map<String, Cumul> motifsTotal = new TreeMap<>();
        long seancesTotal = 0;
        BigDecimal caTotal = BigDecimal.ZERO;
        List<UUID> idsPublies = new ArrayList<>();
        int centresMasques = 0;
        for (CentreInfo centre : centres) {
            Cumul c = parCentre.getOrDefault(centre.id(), new Cumul());
            long nbSeances = seances.getOrDefault(centre.id(), 0L);
            BigDecimal caHt = ca.getOrDefault(centre.id(), BigDecimal.ZERO);
            Map<String, Cumul> motifs = motifsParCentre.getOrDefault(centre.id(), Map.of());
            lignes.add(new CentreAbsences(centre.id(), centre.nom(), centre.actif(), stats(c, nbSeances, caHt),
                    motifs(motifs)));
            if (AnonymityPolicy.mask(c.comptabilisees()) == null) {
                centresMasques++;
                continue;
            }
            idsPublies.add(centre.id());
            total.ajouter(c);
            motifs.forEach((m, v) -> motifsTotal.computeIfAbsent(m, k -> new Cumul()).ajouter(v));
            seancesTotal += nbSeances;
            caTotal = caTotal.add(caHt);
        }
        List<MoisAbsences> mensuel = idsPublies.isEmpty() || finDesAbsences.isBefore(start) ? List.of()
                : mensuel(idsPublies, start, finDesAbsences);
        return new AbsencesOverview(societeId, start, end, OffsetDateTime.now(ZoneOffset.UTC),
                stats(total, seancesTotal, caTotal), lignes, motifs(motifsTotal), mensuel, centresMasques);
    }

    private List<MoisAbsences> mensuel(Collection<UUID> centres, LocalDate start, LocalDate end) {
        List<MoisAbsences> mois = new ArrayList<>();
        for (AbsencesDuPilotage.AbsencesDuMois m : pilotage.mensuel(centres, start, end)) {
            Long masque = AnonymityPolicy.mask(m.nombre());
            mois.add(new MoisAbsences(m.annee(), m.mois(), masque, masque == null ? null : m.montantHt()));
        }
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
     * valorisation HT / chiffre d'affaires HT facturé de la période, en % (null si pas de CA). Tous les chiffres
     * issus des absences comptabilisées sont {@code null} lorsque leur effectif est de 1 à 4 (k-anonymat, KB-ABS-07).
     */
    public record AbsencesStats(Long nbAbsences, Long nbJustifiees, Long nbNonJustifiees, Long nbAQualifier,
                                Long nbRattrapees, long nbSeances, BigDecimal valorisationHt,
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
                                   List<MoisAbsences> mensuel, int centresMasques) {
    }
}
