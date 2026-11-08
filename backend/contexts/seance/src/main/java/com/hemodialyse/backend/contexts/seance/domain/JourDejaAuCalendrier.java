package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;

/**
 * Le centre a déjà un jour de cette nature à cette date : 409 {@code JOUR_DEJA_AU_CALENDRIER} (KB-SEA-58 corrigé : le legacy laissait la
 * contrainte d'unicité de la base répondre 500 {@code DATA_ACCESS_ERROR}).
 */
public class JourDejaAuCalendrier extends RuntimeException {
    private final NatureDeJour nature;

    public JourDejaAuCalendrier(NatureDeJour nature, LocalDate jour) {
        super(nature == NatureDeJour.JOUR_FERIE
                ? "Un jour férié existe déjà à cette date pour ce centre : " + jour
                : "Une fermeture exceptionnelle existe déjà à cette date pour ce centre : " + jour);
        this.nature = nature;
    }

    public NatureDeJour nature() {
        return nature;
    }
}
