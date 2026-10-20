package com.hemodialyse.backend.contexts.facturation.application.validation;

/**
 * Ce que la validation a fait : le nombre de factures émises et de séances facturées.
 */
public record ResultatDeLaValidation(int facturesEmises, int seancesFacturees) {

    static final ResultatDeLaValidation RIEN_EMIS = new ResultatDeLaValidation(0, 0);
}
