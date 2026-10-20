package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulation de facturation d'une période (RG-FAC-022) : l'aperçu des factures que la validation émettrait, avec leurs lignes,
 * leurs totaux et leurs numéros prévisionnels. Elle est calculée à la demande et ne persiste ni facture, ni numéro, ni changement
 * d'état de séance. Ne retient que les séances facturables, dans l'ordre reçu.
 */
public record Simulation(CentreId centre, Instant etablieLe, PeriodeDeFacturation periode,
                         List<FactureSimulee> factures) {

    public Simulation {
        if (centre == null || etablieLe == null || periode == null || factures == null) {
            throw new IllegalArgumentException("Simulation incomplète");
        }
        factures = List.copyOf(factures);
    }

    /**
     * Regroupe les séances en factures selon le regroupement (RG-FAC-011), numérote chaque facture à la suite de la séquence
     * courante et calcule les totaux au taux de TVA donné (celui de la fin de période, KB-FAC-16 corrigé).
     */
    public static Simulation etablir(CentreId centre, Instant etablieLe, PeriodeDeFacturation periode, Regroupement regroupement,
                                     List<SeanceFacturable> seances, TauxDeTva taux, NumerotationPrevisionnelle numerotation) {
        Map<String, List<SeanceFacturable>> groupes = new LinkedHashMap<>();
        for (SeanceFacturable seance : seances) {
            groupes.computeIfAbsent(regroupement.cle(seance), k -> new ArrayList<>()).add(seance);
        }
        List<FactureSimulee> factures = new ArrayList<>();
        for (Map.Entry<String, List<SeanceFacturable>> groupe : groupes.entrySet()) {
            String numero = numerotation.numero(centre, factures.size() + 1);
            factures.add(FactureSimulee.etablir(groupe.getKey(), numero, groupe.getValue(), taux));
        }
        return new Simulation(centre, etablieLe, periode, factures);
    }

    public BigDecimal totalHt() {
        return somme(FactureSimulee::totalHt);
    }

    public BigDecimal totalTva() {
        return somme(FactureSimulee::totalTva);
    }

    public BigDecimal totalTtc() {
        return somme(FactureSimulee::totalTtc);
    }

    private BigDecimal somme(java.util.function.Function<FactureSimulee, BigDecimal> montant) {
        return factures.stream().map(montant).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
