package com.hemodialyse.backend.contexts.documents.contrat;

import com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.HistoriquesEnMemoire;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersionsRepository;
import org.junit.jupiter.api.BeforeEach;

/** Le double en mémoire passe le contrat de l'historique des versions. */
class HistoriqueDesVersionsEnMemoireContratTest extends HistoriqueDesVersionsContrat {

    private HistoriquesEnMemoire memoire;

    @BeforeEach
    void vider() {
        memoire = new HistoriquesEnMemoire();
    }

    @Override
    protected HistoriqueDesVersionsRepository historiques() {
        return memoire;
    }

    @Override
    protected GabaritPersonnaliseDuCentre pourLeMoteur() {
        return memoire;
    }
}
