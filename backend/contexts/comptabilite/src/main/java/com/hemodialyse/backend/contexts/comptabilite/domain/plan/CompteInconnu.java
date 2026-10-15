package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Le compte n'est pas un compte actif du plan du centre ({@code COMPTE_INCONNU}).
 */
public class CompteInconnu extends RegleMetierViolee {
    public CompteInconnu(NumeroDeCompte compte) {
        this(compte.toString());
    }

    /**
     * Un numéro tel que saisi (le mapping le compare sans normalisation).
     */
    public CompteInconnu(String compte) {
        super("COMPTE_INCONNU", "Le compte " + compte + " n'existe pas dans le plan comptable du centre ou est désactivé");
    }
}
