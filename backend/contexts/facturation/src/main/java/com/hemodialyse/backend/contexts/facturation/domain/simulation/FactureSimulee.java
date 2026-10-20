package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facture telle qu'elle serait émise pour un groupe de séances (RG-FAC-022) : lignes par forfait, totaux HT, TVA et TTC arrondis
 * à deux décimales, numéro prévisionnel. Elle n'existe que dans la simulation.
 *
 * @param cle clé du groupe (patient, ou patient et forfait) qui identifie la facture dans l'écran de simulation
 */
public record FactureSimulee(String cle, String numeroPrevisionnel, PatientFacture patient, List<LigneSimulee> lignes,
                             List<SeanceFacturable> seances, BigDecimal totalHt, BigDecimal totalTva,
                             BigDecimal totalTtc) {

    /**
     * Facture d'un groupe de séances d'un même patient : une ligne par forfait (même identifiant et même libellé), dans l'ordre
     * des séances ; la TVA est celle du taux sur le total HT.
     */
    static FactureSimulee etablir(String cle, String numeroPrevisionnel, List<SeanceFacturable> seances, TauxDeTva taux) {
        Map<String, List<SeanceFacturable>> parForfait = new LinkedHashMap<>();
        for (SeanceFacturable seance : seances) {
            parForfait.computeIfAbsent(seance.forfait().cleDeLigne(), k -> new java.util.ArrayList<>()).add(seance);
        }
        List<LigneSimulee> lignes = parForfait.values().stream()
                .map(groupe -> new LigneSimulee(groupe.getFirst().forfait(), groupe.size()))
                .toList();
        BigDecimal ht = lignes.stream().map(LigneSimulee::montantHt).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tva = taux.tvaSur(ht);
        BigDecimal ttc = ht.add(tva).setScale(2, RoundingMode.HALF_UP);
        return new FactureSimulee(cle, numeroPrevisionnel, seances.getFirst().patient(), lignes, List.copyOf(seances), ht, tva, ttc);
    }
}
