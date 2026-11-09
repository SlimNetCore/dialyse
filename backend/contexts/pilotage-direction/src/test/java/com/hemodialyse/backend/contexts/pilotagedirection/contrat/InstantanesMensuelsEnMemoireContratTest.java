package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesEnMemoire;
import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels;

/** Le double en mémoire respecte le contrat des instantanés mensuels. */
class InstantanesMensuelsEnMemoireContratTest extends InstantanesMensuelsContrat {

    private final InstantanesEnMemoire instantanes = new InstantanesEnMemoire();

    @Override
    protected InstantanesMensuels instantanes() {
        return instantanes;
    }
}
