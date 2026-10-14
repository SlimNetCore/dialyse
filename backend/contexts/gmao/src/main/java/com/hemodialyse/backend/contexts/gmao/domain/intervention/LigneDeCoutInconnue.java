package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/** La ligne de coût à retirer n'appartient pas à l'intervention. 400 BAD_REQUEST observé. */
public class LigneDeCoutInconnue extends IllegalArgumentException {
    public LigneDeCoutInconnue() {
        super("Ligne de coût non trouvée");
    }
}
