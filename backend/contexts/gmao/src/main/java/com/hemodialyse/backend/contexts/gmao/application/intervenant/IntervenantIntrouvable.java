package com.hemodialyse.backend.contexts.gmao.application.intervenant;

/**
 * Intervenant inconnu, ou d'un autre centre (indistinguable, KB-GMAO-23), sur une commande. Étend
 * {@link IllegalArgumentException} pour reproduire le contrat observé : HTTP 400 BAD_REQUEST « Intervenant non trouvé »
 * et non 404.
 */
public class IntervenantIntrouvable extends IllegalArgumentException {
    public IntervenantIntrouvable() {
        super("Intervenant non trouvé");
    }
}
