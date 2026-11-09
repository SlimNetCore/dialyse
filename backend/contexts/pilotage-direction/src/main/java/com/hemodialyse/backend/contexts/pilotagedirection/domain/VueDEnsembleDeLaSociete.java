package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * La vue d'ensemble de la direction d'une société sur une période : un bilan par centre (dans l'ordre des centres de la société, les
 * centres inactifs compris), le bilan consolidé de la société, l'évolution mensuelle et la période précédente de même durée.
 * <p>
 * Règles (RG-DIR) : une société SANS centre rend des totaux nuls, aucune liste et AUCUNE période précédente ; l'encaissé nul est servi
 * {@code 0.00} ; les totaux sont les sommes des valeurs BRUTES des centres (KB-DIR-01) ; l'évolution mensuelle est triée par
 * {@code AAAA-MM|centre}, un mois présent d'un seul côté (séances ou finances) est complété par zéro.
 */
public record VueDEnsembleDeLaSociete(SocieteSuivie societe, Periode periode, OffsetDateTime genereLe, List<BilanDuCentre> centres,
                                      BilanDuCentre totaux, List<PointMensuel> mensuel, ActiviteDeLaPeriodePrecedente precedente) {

    public VueDEnsembleDeLaSociete {
        centres = List.copyOf(centres);
        mensuel = List.copyOf(mensuel);
    }

    /** La vue d'une société sans centre : rien à compter, rien à comparer. */
    public static VueDEnsembleDeLaSociete sansCentre(SocieteSuivie societe, Periode periode, OffsetDateTime genereLe) {
        return new VueDEnsembleDeLaSociete(societe, periode, genereLe, List.of(), BilanDuCentre.sansActivite(), List.of(), null);
    }

    public static VueDEnsembleDeLaSociete constituer(SocieteSuivie societe, Periode periode, OffsetDateTime genereLe,
                                                     Map<UUID, EffectifDuCentre> effectifs, Map<UUID, Long> seances,
                                                     Map<UUID, ChiffresDuCentre> chiffres, List<MoisDeSeances> seancesParMois,
                                                     List<MoisDeChiffres> chiffresParMois,
                                                     ActiviteDeLaPeriodePrecedente precedente) {
        List<BilanDuCentre> bilans = new ArrayList<>();
        for (CentreDeLaSociete centre : societe.centres()) {
            EffectifDuCentre effectif = effectifs.getOrDefault(centre.id(), EffectifDuCentre.aucun());
            long nombreDeSeances = seances.getOrDefault(centre.id(), 0L);
            ChiffresDuCentre facture = chiffres.getOrDefault(centre.id(), ChiffresDuCentre.aucun());
            BigDecimal encaisse = facture.montants().encaisse().signum() == 0
                    ? ChiffresFinanciers.montantNul() : facture.montants().encaisse();
            bilans.add(new BilanDuCentre(centre.id(), centre.nom(), centre.actif(), effectif.patients(), effectif.sousKt(),
                    nombreDeSeances, facture.factures(),
                    new ChiffresFinanciers(facture.montants().caHt(), facture.montants().caTtc(), encaisse)));
        }
        return new VueDEnsembleDeLaSociete(societe, periode, genereLe, bilans, BilanDuCentre.consolider(bilans),
                evolutionMensuelle(seancesParMois, chiffresParMois), precedente);
    }

    /** Fusionne les séances et le chiffre d'affaires par (mois, centre) ; une ligne par couple présent d'un côté au moins. */
    static List<PointMensuel> evolutionMensuelle(List<MoisDeSeances> seancesParMois, List<MoisDeChiffres> chiffresParMois) {
        Map<String, Long> seances = new HashMap<>();
        for (MoisDeSeances m : seancesParMois) {
            seances.put(cle(m.centreId(), m.annee(), m.mois()), m.nombre());
        }
        Map<String, MoisDeChiffres> chiffres = new HashMap<>();
        for (MoisDeChiffres m : chiffresParMois) {
            chiffres.put(cle(m.centreId(), m.annee(), m.mois()), m);
        }
        TreeSet<String> cles = new TreeSet<>(seances.keySet());
        cles.addAll(chiffres.keySet());
        List<PointMensuel> points = new ArrayList<>();
        for (String cle : cles) {
            String[] parties = cle.split("\\|");
            MoisDeChiffres ca = chiffres.get(cle);
            points.add(new PointMensuel(parties[0], UUID.fromString(parties[1]), seances.getOrDefault(cle, 0L),
                    ca == null ? ChiffresFinanciers.montantNul() : ca.caHt(),
                    ca == null ? ChiffresFinanciers.montantNul() : ca.caTtc()));
        }
        return points;
    }

    private static String cle(UUID centre, int annee, int mois) {
        return String.format("%04d-%02d|%s", annee, mois, centre);
    }
}
