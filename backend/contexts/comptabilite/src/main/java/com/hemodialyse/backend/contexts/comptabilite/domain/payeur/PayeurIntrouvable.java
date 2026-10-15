package com.hemodialyse.backend.contexts.comptabilite.domain.payeur;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Le payeur n'existe pas dans ce centre ({@code PAYEUR_INTROUVABLE}).
 */
public class PayeurIntrouvable extends RegleMetierViolee {
    public PayeurIntrouvable() {
        super("PAYEUR_INTROUVABLE", "Payeur introuvable dans ce centre");
    }
}
