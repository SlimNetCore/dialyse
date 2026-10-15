package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

/**
 * État d'une écriture comptable : {@code BROUILLON → VALIDEE → EXPORTEE} ; une écriture exportée est immuable.
 */
public enum StatutEcriture {
    BROUILLON, VALIDEE, EXPORTEE;

    /**
     * Préfixe du message historique d'un statut inconnu (nom de classe du legacy), reproduit à l'identique (KB-CPT-24 à 34).
     */
    private static final String MESSAGE_HISTORIQUE = "No enum constant com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture.";

    /**
     * Le statut qui porte exactement ce nom (casse comprise) ; refus ({@link IllegalArgumentException}, 400) sinon.
     */
    public static StatutEcriture depuis(String nom) {
        for (StatutEcriture statut : values()) {
            if (statut.name().equals(nom)) {
                return statut;
            }
        }
        throw new IllegalArgumentException(MESSAGE_HISTORIQUE + nom);
    }
}
