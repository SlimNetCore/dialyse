package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/** Un prix d'acquisition est positif ou nul. Message identique au legacy (parité). */
public class PrixNegatif extends IllegalArgumentException {
    public PrixNegatif() {
        super("Le prix d'acquisition ne peut pas être négatif");
    }
}
