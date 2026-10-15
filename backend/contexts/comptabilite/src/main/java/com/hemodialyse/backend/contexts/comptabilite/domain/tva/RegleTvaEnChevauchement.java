package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import java.time.LocalDate;

/**
 * La règle chevauche une règle active du même centre et du même type de prestation (400 observé, KB-CPT-16).
 */
public class RegleTvaEnChevauchement extends IllegalArgumentException {
    RegleTvaEnChevauchement(RegleTva nouvelle, RegleTva existante) {
        super("La règle de TVA " + nouvelle.typePrestation() + " " + periode(nouvelle.debutValidite(), nouvelle.finValidite())
                + " chevauche la règle existante " + periode(existante.debutValidite(), existante.finValidite())
                + " : les dates de deux règles du même type ne doivent pas se recouvrir");
    }

    private static String periode(LocalDate debut, LocalDate fin) {
        return fin == null ? "à partir du " + debut : "du " + debut + " au " + fin;
    }
}
