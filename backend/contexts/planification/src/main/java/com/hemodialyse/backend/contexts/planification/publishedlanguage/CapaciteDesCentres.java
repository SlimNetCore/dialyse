package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port publié (lecture) : la capacité hebdomadaire de placement d'un centre, pour le pilotage de la direction. Places = générateurs
 * en service x créneaux x jours d'ouverture ; occupées = places prises par les patients actifs au jour donné. Bornée au centre.
 */
public interface CapaciteDesCentres {

    record CapaciteDuCentre(int generateursEnService, int creneaux, int joursOuverts, int places, int occupees) {
        public int libres() {
            return Math.max(0, places - occupees);
        }
    }

    CapaciteDuCentre duCentre(UUID centre, LocalDate le);
}
