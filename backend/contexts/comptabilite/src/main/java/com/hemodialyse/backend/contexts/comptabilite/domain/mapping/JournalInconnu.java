package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

/**
 * Le journal choisi pour une opération n'existe pas dans le centre ou est désactivé ({@code JOURNAL_INCONNU}).
 */
public class JournalInconnu extends RegleMetierViolee {
    JournalInconnu(CodeJournal journal, OperationComptable operation) {
        super("JOURNAL_INCONNU", "Le journal " + journal + " choisi pour " + operation + " n'existe pas ou est désactivé");
    }
}
