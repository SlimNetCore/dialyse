package com.hemodialyse.backend.contexts.planification.interfaces;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateurDevenuIndisponible;
import com.hemodialyse.backend.contexts.planification.application.AlerterUnGenerateurIndisponible;
import com.hemodialyse.backend.contexts.planification.doubles.PlacesEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.interfaces.evenements.GenerateurDevenuIndisponibleEcouteur;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L'écouteur de l'événement publié {@code gmao.v1} : traduction vers le cas d'usage, idempotence, filtre des statuts. */
class GenerateurDevenuIndisponibleEcouteurTest {

    private static final UUID CENTRE = UUID.fromString("99997805-0000-0000-0000-0000000000d1");
    private static final UUID GENERATEUR = UUID.fromString("99997805-0000-0000-0000-0000000000d2");

    private final PlacesEnMemoire monde = new PlacesEnMemoire(new RessourcesDuCentre(null, null, null, null, null));
    private final GenerateurDevenuIndisponibleEcouteur ecouteur = new GenerateurDevenuIndisponibleEcouteur(
            new AlerterUnGenerateurIndisponible(monde, monde, monde, monde, monde));

    private void evenement(String precedent, String statut) throws Exception {
        Method m = GenerateurDevenuIndisponibleEcouteur.class.getDeclaredMethod("surGenerateurDevenuIndisponible",
                GenerateurDevenuIndisponible.class);
        m.setAccessible(true);
        m.invoke(ecouteur, new GenerateurDevenuIndisponible(GENERATEUR, CENTRE, "G1", "Equipement G1", precedent, statut, Instant.now()));
    }

    @Test
    void should_be_registered_as_an_application_module_listener_so_that_a_failed_alert_is_replayed() throws Exception {
        Method m = GenerateurDevenuIndisponibleEcouteur.class.getDeclaredMethod("surGenerateurDevenuIndisponible",
                GenerateurDevenuIndisponible.class);
        assertThat(m.isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
    }

    @Test
    void should_alert_with_the_patients_of_the_generator_and_never_move_them() throws Exception {
        PatientId alice = new PatientId(UUID.randomUUID());
        monde.patient(alice, "DUPONT Alice", new Occupation(alice, new SalleId(UUID.randomUUID()), new CreneauId(UUID.randomUUID()),
                new GenerateurId(GENERATEUR), Set.of(JourDeLaSemaine.LUNDI), null));

        evenement("EN_SERVICE", "HORS_SERVICE");

        assertThat(monde.alertes).containsExactly("GENERATEUR_INDISPONIBLE|G1|HORS_SERVICE|1|DUPONT Alice");
        assertThat(monde.ecritures).isZero();
    }

    @Test
    void should_alert_once_per_generator_and_day_when_the_event_is_delivered_again() throws Exception {
        evenement("EN_SERVICE", "EN_MAINTENANCE");
        evenement("EN_SERVICE", "EN_MAINTENANCE");

        assertThat(monde.alertes).hasSize(1);
    }

    @Test
    void should_ignore_a_generator_that_was_not_in_service_or_still_is() throws Exception {
        evenement("HORS_SERVICE", "REFORME");
        evenement("EN_SERVICE", "EN_SERVICE");
        evenement(null, "HORS_SERVICE");

        assertThat(monde.alertes).isEmpty();
    }
}
