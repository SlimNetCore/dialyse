package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les indicateurs de la direction d'une société sur une période : cliniques et stock de chaque centre, totaux, et alertes levées par la
 * {@link PolitiqueDAlertes}. Composés (PD6, ADR 0106) à partir de ce que les autres contextes publient ; aucune lecture ici.
 * <p>
 * Règles reproduites du legacy : le stock de la société est la somme des valeurs NON arrondies des centres (voir {@link StockDuCentre}) ;
 * les totaux cliniques sont ceux que le Dossier médical publie (calculés sur les bruts : KB-DIR-01) ; les alertes de taux lisent les taux
 * DÉJÀ masqués ; une société sans centre a des totaux à zéro, sans centre ni alerte.
 */
public record IndicateursDeLaSociete(UUID societeId, Periode periode, OffsetDateTime genereLe, List<IndicateursDuCentre> centres,
                                     IndicateursDuCentre totaux, List<AlerteLevee> alertes) {

    public IndicateursDeLaSociete {
        centres = List.copyOf(centres);
        alertes = List.copyOf(alertes);
    }

    /**
     * @param stocks le stock de chaque centre, évalué à la fin de la période ; un centre absent a un stock vide
     */
    public static IndicateursDeLaSociete composer(SocieteSuivie societe, Periode periode, OffsetDateTime genereLe, CliniquesDesCentres cliniques,
                                                  Map<UUID, StockDuCentre> stocks) {
        if (societe.estSansCentre()) {
            return new IndicateursDeLaSociete(societe.id(), periode, genereLe, List.of(),
                    IndicateursDuCentre.totaux(cliniques.consolides(), StockDuCentre.VIDE.arrondi()), List.of());
        }
        List<IndicateursDuCentre> centres = new ArrayList<>();
        List<AlerteLevee> alertes = new ArrayList<>();
        StockDuCentre total = StockDuCentre.VIDE;
        for (CentreDeLaSociete c : societe.centres()) {
            IndicateursCliniquesDuCentre clinique = cliniques.parCentre().get(c.id());
            StockDuCentre stock = stocks.getOrDefault(c.id(), StockDuCentre.VIDE);
            centres.add(new IndicateursDuCentre(c.id(), c.nom(), c.actif(), clinique, stock.arrondi()));
            total = total.plus(stock);
            alertes.addAll(PolitiqueDAlertes.lever(new ConstatDuCentre(c.id(), c.nom(), clinique.ktV().pctDansCible(),
                    clinique.hemoglobine().pctDansCible(), clinique.observanceEnRetard(), stock.articlesSousSeuil(), stock.lotsPerimes(),
                    stock.lotsProches())));
        }
        return new IndicateursDeLaSociete(societe.id(), periode, genereLe, centres,
                IndicateursDuCentre.totaux(cliniques.consolides(), total.arrondi()), alertes);
    }
}
