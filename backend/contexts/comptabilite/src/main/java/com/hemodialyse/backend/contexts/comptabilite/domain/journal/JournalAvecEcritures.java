package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Le journal porte des écritures : il se désactive, il ne se supprime pas ({@code JOURNAL_AVEC_ECRITURES}).
 */
public class JournalAvecEcritures extends RegleMetierViolee {
    JournalAvecEcritures(CodeJournal code) {
        super("JOURNAL_AVEC_ECRITURES", "Le journal " + code + " porte des écritures : désactivez-le au lieu de le supprimer");
    }
}
