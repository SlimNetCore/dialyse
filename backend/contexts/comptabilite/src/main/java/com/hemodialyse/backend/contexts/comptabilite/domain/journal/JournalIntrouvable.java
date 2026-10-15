package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Aucun journal ne porte ce code dans le centre ({@code JOURNAL_INTROUVABLE}).
 */
public class JournalIntrouvable extends RegleMetierViolee {
    public JournalIntrouvable(CodeJournal code) {
        super("JOURNAL_INTROUVABLE", "Journal " + code + " introuvable");
    }
}
