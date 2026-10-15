package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Aucun compte enregistré ne porte ce numéro dans le centre ({@code COMPTE_INTROUVABLE}).
 */
public class CompteIntrouvable extends RegleMetierViolee {
    public CompteIntrouvable(NumeroDeCompte compte) {
        super("COMPTE_INTROUVABLE", "Compte " + compte + " introuvable");
    }
}
