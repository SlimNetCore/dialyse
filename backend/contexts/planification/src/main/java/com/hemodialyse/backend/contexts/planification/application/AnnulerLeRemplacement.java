package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;

/**
 * Annule un remplacement du centre ({@code REMPLACEMENT_INTROUVABLE} sinon).
 */
public class AnnulerLeRemplacement {
    private final UniteDeTravail uniteDeTravail;
    private final RemplacementsRepository remplacements;

    public AnnulerLeRemplacement(UniteDeTravail uniteDeTravail, RemplacementsRepository remplacements) {
        this.uniteDeTravail = uniteDeTravail;
        this.remplacements = remplacements;
    }

    public void executer(CentreId centre, RemplacementId id) {
        uniteDeTravail.executer(() -> {
            remplacements.parId(centre, id).orElseThrow(
                    () -> new RegleDePlanificationViolee("REMPLACEMENT_INTROUVABLE", "Remplacement introuvable"));
            remplacements.annuler(centre, id);
            return null;
        });
    }
}
