package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * L'écriture n'existe pas dans le centre, ou appartient à un autre centre ({@code ECRITURE_INTROUVABLE}, 422).
 */
public class EcritureIntrouvable extends RegleMetierViolee {
    public EcritureIntrouvable() {
        super("ECRITURE_INTROUVABLE", "Écriture introuvable");
    }
}
