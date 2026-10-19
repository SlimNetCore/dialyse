package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Une pièce saisie garde au moins un montant au débit et un au crédit une fois les lignes à zéro omises
 * ({@code PIECE_MONTANTS_INVALIDES}, RG-CPT-021).
 */
public class PieceIncomplete extends RegleMetierViolee {
    PieceIncomplete() {
        super("PIECE_MONTANTS_INVALIDES", "Une pièce comporte au moins un montant au débit et un montant au crédit");
    }
}

