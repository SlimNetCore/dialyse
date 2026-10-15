package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.doubles.JournauxEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;

/**
 * Le double en mémoire passe le contrat partagé des journaux.
 */
class JournalEnMemoireContratTest extends JournalContrat {

    private final JournauxEnMemoire memoire = new JournauxEnMemoire();

    @Override
    protected JournalRepository repository() {
        return memoire;
    }
}
