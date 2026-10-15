package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Le journal est choisi pour une opération ou un modèle : il ne se désactive ni ne se supprime ({@code JOURNAL_UTILISE}).
 */
public class JournalUtilise extends RegleMetierViolee {
    JournalUtilise(CodeJournal code, String action) {
        super("JOURNAL_UTILISE", "Le journal " + code + " est choisi pour une opération : changez-la avant de le " + action);
    }
}
