package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;

/**
 * La salle et le créneau d'une case doivent appartenir au centre (422 {@code AFFECTATION_SALLE_INCONNUE} puis {@code AFFECTATION_CRENEAU_INCONNU}).
 */
final class CaseDuCentre {
    private CaseDuCentre() {
    }

    static void exiger(SallesDuCentre salles, CreneauxDuCentre creneaux, CentreId centre, SalleId salle, CreneauId creneau) {
        if (!salles.toutes(centre).contains(salle)) {
            throw new RegleDePlanificationViolee("AFFECTATION_SALLE_INCONNUE", "Cette salle n'appartient pas au centre");
        }
        if (!creneaux.contient(centre, creneau)) {
            throw new RegleDePlanificationViolee("AFFECTATION_CRENEAU_INCONNU", "Ce créneau n'appartient pas au centre");
        }
    }
}
