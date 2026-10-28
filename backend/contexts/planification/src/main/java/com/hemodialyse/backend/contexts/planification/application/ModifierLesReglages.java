package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;

/**
 * Remplace les réglages de planification du centre (RG-PLN-098) sous le verrou du centre.
 */
public class ModifierLesReglages {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuCentre verrou;
    private final ReglagesRepository reglages;

    public ModifierLesReglages(UniteDeTravail uniteDeTravail, VerrouDuCentre verrou, ReglagesRepository reglages) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.reglages = reglages;
    }

    public ReglagesDePlanification executer(CentreId centre, ReglagesDePlanification nouveaux) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            reglages.enregistrer(centre, nouveaux);
            return nouveaux;
        });
    }
}
