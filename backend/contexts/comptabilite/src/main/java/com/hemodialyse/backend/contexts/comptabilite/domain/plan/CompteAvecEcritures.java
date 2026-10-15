package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Le compte porte des écritures : il se désactive, il ne se supprime pas ({@code COMPTE_AVEC_ECRITURES}).
 */
public class CompteAvecEcritures extends RegleMetierViolee {
    CompteAvecEcritures(NumeroDeCompte compte) {
        super("COMPTE_AVEC_ECRITURES", "Le compte " + compte + " porte des écritures : désactivez-le au lieu de le supprimer");
    }
}
