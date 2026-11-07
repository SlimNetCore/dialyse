package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Le patient a plusieurs séances le même jour : le scan ne sait pas laquelle prendre. 409 {@code SEANCE_DU_JOUR_AMBIGUE} (KB-SEA-02,
 * corrige le 500 du legacy ; aucune unicité par patient et par jour n'est inventée).
 */
public class SeanceDuJourAmbigue extends RuntimeException {
    public SeanceDuJourAmbigue() {
        super("Ce patient a plusieurs séances ce jour : choisissez-en une depuis l'historique");
    }
}
