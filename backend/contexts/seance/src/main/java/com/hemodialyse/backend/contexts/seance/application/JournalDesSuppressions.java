package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SuppressionDeSeance;

/** Port sortant : le journal des séances supprimées (table {@code seance_suppression}), écrit dans l'unité de travail de la suppression. */
public interface JournalDesSuppressions {
    void enregistrer(SuppressionDeSeance suppression);
}
