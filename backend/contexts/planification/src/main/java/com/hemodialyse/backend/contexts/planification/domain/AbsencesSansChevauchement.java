package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

/**
 * Règle d'ensemble des absences d'un infirmier (décision du 26/10/2026, KB-PLN-26) : deux absences du même infirmier ne partagent
 * jamais un jour (doublon, chevauchement partiel et absence incluse refusés ; absences contiguës acceptées). À appeler sous le
 * verrou de la fiche de l'infirmier.
 */
public final class AbsencesSansChevauchement {
    private AbsencesSansChevauchement() {
    }

    public static void verifier(AbsencesRepository absences, CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode) {
        if (absences.existeUneAbsenceSurLaPeriode(centre, infirmier, periode)) {
            throw new RegleDePlanificationViolee("ABSENCE_CHEVAUCHEMENT",
                    "Cet infirmier a déjà une absence qui couvre au moins un de ces jours");
        }
    }
}
