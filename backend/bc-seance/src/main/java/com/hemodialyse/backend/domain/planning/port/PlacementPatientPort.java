package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.util.Set;
import java.util.UUID;

/**
 * Port de sortie : placement d'un patient (salle, créneau, générateur, jours de dialyse), toujours borné au centre.
 */
public interface PlacementPatientPort {

    /**
     * Placement enregistré sur la fiche ; {@link Placement#vide()} si le patient n'est pas placé ou inconnu.
     */
    Placement placementActuel(UUID centerId, UUID patientId);

    /**
     * Change la salle, le créneau et le générateur du patient ; ses jours de dialyse ne changent pas.
     */
    void deplacer(UUID centerId, UUID patientId, UUID salleId, UUID creneauId, UUID generateurId);

    /**
     * Nom complet du patient (messages et notifications), vide si inconnu.
     */
    String nomPatient(UUID centerId, UUID patientId);

    record Placement(UUID salleId, UUID creneauId, UUID generateurId, Set<JourSemaine> jours) {

        public Placement {
            jours = jours == null ? Set.of() : Set.copyOf(jours);
        }

        public static Placement vide() {
            return new Placement(null, null, null, Set.of());
        }

        public boolean estVide() {
            return salleId == null && creneauId == null && generateurId == null && jours.isEmpty();
        }
    }
}
