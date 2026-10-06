package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.SuppressionSeance;

/**
 * Journal des séances supprimées (traçabilité des données de soin effacées).
 */
public interface SuppressionSeanceJournalPort {

    void enregistrer(SuppressionSeance suppression);
}
