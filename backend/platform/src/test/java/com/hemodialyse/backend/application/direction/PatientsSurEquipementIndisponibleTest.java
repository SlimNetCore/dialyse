package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PatientsAuxGenerateurs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le chiffre « patients sur équipement indisponible » : seuls les générateurs INDISPONIBLES DU CENTRE sont demandés à Planification
 * (revue P6 : un patient rattaché au générateur d'un autre centre, compté par l'ancienne jointure, ne l'est plus).
 */
class PatientsSurEquipementIndisponibleTest {

    private static final UUID CENTRE = UUID.fromString("99997806-0000-0000-0000-0000000000c1");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99997806-0000-0000-0000-0000000000c2");

    private static Generateur generateur(int n, String statut, UUID centre) {
        return new Generateur(new UUID(0x9999, n), "G" + n, "Générateur " + n, null, statut, centre);
    }

    @Test
    void should_ask_the_planning_only_for_the_unavailable_generators_of_the_centre() {
        List<UUID> demandes = new ArrayList<>();
        GenerateursDuCentre parc = centre -> centre.equals(CENTRE)
                ? List.of(generateur(1, "EN_SERVICE", CENTRE), generateur(2, "EN_MAINTENANCE", CENTRE),
                generateur(3, "EN_ATTENTE_PIECE", CENTRE), generateur(4, "HORS_SERVICE", CENTRE), generateur(5, "A_REFORMER", CENTRE),
                generateur(6, "REFORME", CENTRE))
                : List.of(generateur(7, "HORS_SERVICE", AUTRE_CENTRE));
        PatientsAuxGenerateurs patients = (centre, generateurs) -> {
            assertEquals(CENTRE, centre);
            demandes.addAll(generateurs);
            return generateurs.size();
        };

        long nombre = new DirectionGmaoQueryService(null, null, parc, patients).countPatientsSurEquipementIndisponible(CENTRE);

        assertEquals(5, nombre);
        assertEquals(5, demandes.size());
        assertTrue(demandes.stream().noneMatch(id -> id.equals(new UUID(0x9999, 1)) || id.equals(new UUID(0x9999, 7))),
                "ni le générateur en service, ni celui d'un autre centre");
    }
}
