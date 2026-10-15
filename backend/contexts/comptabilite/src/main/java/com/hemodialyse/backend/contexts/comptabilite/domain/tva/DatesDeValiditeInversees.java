package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import java.time.LocalDate;

/**
 * La date de fin de validité précède la date de début : la règle ne s'appliquerait jamais (400 observé, KB-CPT-16).
 */
public class DatesDeValiditeInversees extends IllegalArgumentException {
    DatesDeValiditeInversees(LocalDate debut, LocalDate fin) {
        super("La date de fin de validité (" + fin + ") précède la date de début (" + debut + ") : la règle ne s'appliquerait jamais");
    }
}
