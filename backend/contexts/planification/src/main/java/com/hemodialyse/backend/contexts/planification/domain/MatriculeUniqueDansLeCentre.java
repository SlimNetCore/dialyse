package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

/**
 * Règle d'ensemble (RG-INF-001) : le matricule d'une fiche est unique dans son centre, sans tenir compte de la casse. Service de
 * domaine sans état ; à appeler sous le {@link VerrouDuReferentielDesInfirmiers}. Refus 422 {@code INFIRMIER_MATRICULE_EXISTANT}.
 */
public final class MatriculeUniqueDansLeCentre {
    private MatriculeUniqueDansLeCentre() {
    }

    /** Refuse le matricule s'il est déjà porté par une autre fiche que {@code ficheCourante} ({@code null} à la création). */
    public static void verifier(InfirmiersRepository infirmiers, CentreId centre, Matricule matricule, InfirmierId ficheCourante) {
        infirmiers.parMatricule(centre, matricule).filter(autre -> !autre.id().equals(ficheCourante)).ifPresent(autre -> {
            throw new RegleDePlanificationViolee("INFIRMIER_MATRICULE_EXISTANT", "Ce matricule existe déjà dans le centre");
        });
    }
}
