package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ProfilDeLectureDesOrdonnances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** L'impression d'une ordonnance exige le profil de lecture des ordonnances puis le centre de la session, avant toute donnée. */
class GarderLImpressionDeLOrdonnanceTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private final List<UUID> centresDemandes = new ArrayList<>();
    private final SessionCourante session = demande -> {
        centresDemandes.add(demande);
        if (demande != null && !demande.equals(CENTRE)) throw new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session");
        return new CentreId(CENTRE);
    };

    private GarderLImpressionDeLOrdonnance avecLesRoles(String... roles) {
        return new GarderLImpressionDeLOrdonnance(() -> Set.of(roles), session);
    }

    @Test
    void should_let_a_physician_or_an_administrator_print_in_their_centre() {
        avecLesRoles("ROLE_MEDECIN").executer(CENTRE);
        avecLesRoles("ROLE_ADMIN").executer(null);
        avecLesRoles("ROLE_INFIRMIER", "ROLE_MEDECIN").executer(CENTRE);

        assertThat(centresDemandes).containsExactly(CENTRE, null, CENTRE);
    }

    @Test
    void should_refuse_any_other_profile_before_looking_at_the_centre() {
        for (String role : List.of("ROLE_SECRETAIRE", "ROLE_INFIRMIER", "ROLE_DIRECTION", "ROLE_SUPERADMIN", "ROLE_SOCIETE_ADMIN")) {
            assertThatThrownBy(() -> avecLesRoles(role).executer(CENTRE)).isInstanceOf(AccesRefuse.class)
                    .hasMessage("Profil insuffisant pour imprimer une ordonnance");
        }
        assertThatThrownBy(() -> avecLesRoles().executer(CENTRE)).isInstanceOf(AccesRefuse.class);
        assertThat(centresDemandes).isEmpty();
    }

    @Test
    void should_refuse_a_centre_which_is_not_the_one_of_the_session() {
        assertThatThrownBy(() -> avecLesRoles("ROLE_MEDECIN").executer(UUID.randomUUID())).isInstanceOf(AccesRefuse.class)
                .hasMessageContaining("ne correspond pas à la session");
    }

    @Test
    void should_read_the_reading_profile_of_prescriptions_on_the_roles_of_the_token() {
        assertThat(ProfilDeLectureDesOrdonnances.autorise(Set.of("ROLE_MEDECIN"))).isTrue();
        assertThat(ProfilDeLectureDesOrdonnances.autorise(Set.of("ROLE_ADMIN", "ROLE_X"))).isTrue();
        assertThat(ProfilDeLectureDesOrdonnances.autorise(Set.of("MEDECIN"))).isFalse();
        assertThat(ProfilDeLectureDesOrdonnances.autorise(Set.of())).isFalse();
    }
}
