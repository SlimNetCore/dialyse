package com.hemodialyse.backend.contexts.comptabilite.application.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;

/**
 * Un journal, tel que l'écran le lit.
 */
public record JournalVue(String code, String libelle, boolean actif) {

    public static JournalVue depuis(Journal journal) {
        return new JournalVue(journal.code().valeur(), journal.libelle(), journal.actif());
    }
}
