package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Le début d'une période de statistiques est après sa fin : 400 {@code INVALID_DATE_RANGE} (code reproduit du legacy, les clients le testent). */
public class PeriodeInversee extends SaisieInvalide {
    public PeriodeInversee() {
        super("La date de début doit être <= à la date de fin");
    }
}
