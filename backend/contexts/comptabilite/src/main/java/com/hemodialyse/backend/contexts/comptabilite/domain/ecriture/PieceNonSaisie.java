package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Seule une pièce saisie à partir d'un modèle s'extourne ({@code PIECE_NON_SAISIE}, RG-CPT-022).
 */
public class PieceNonSaisie extends RegleMetierViolee {
    PieceNonSaisie() {
        super("PIECE_NON_SAISIE", "Seule une pièce saisie peut être extournée : cette écriture est générée par le système");
    }
}

