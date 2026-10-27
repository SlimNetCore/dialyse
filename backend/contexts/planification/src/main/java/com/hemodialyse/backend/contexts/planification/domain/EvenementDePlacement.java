package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;

/**
 * Événements de domaine des places du centre (internes, non publiés, catalogue du tactique §7.1) : levés par
 * {@link PlacesDuCentre}, lus par le cas d'usage pour notifier.
 */
public sealed interface EvenementDePlacement {

    PatientId patient();

    /** Un patient sans place en a reçu une. */
    record PatientPlace(PatientId patient, Place place) implements EvenementDePlacement {
    }

    /** La place d'un patient déjà placé a changé. */
    record PlacementModifie(PatientId patient, Occupation avant, Place apres) implements EvenementDePlacement {
    }

    /** La fiche n'a plus de place. */
    record PlaceRetiree(PatientId patient, Occupation avant) implements EvenementDePlacement {
    }

    /** Un patient devenu à risque a été replacé en salle d'isolement (jours inchangés). */
    record PatientReplaceEnIsolement(PatientId patient, Occupation avant, Place apres, Salle salle)
            implements EvenementDePlacement {
    }

    /** Un patient devenu à risque n'a pu être replacé faute de place d'isolement : il reste en place. */
    record IsolementImpossible(PatientId patient) implements EvenementDePlacement {
    }
}
