package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A2;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.HORS_SERVICE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P25 (RG-PLN-096) : un déplacement temporaire a une cible complète et COHÉRENTE avec les ressources du centre, refusée sinon
 * (décision du 28/10, KB-PLN-105).
 */
class DeplacementTemporaireTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 4);

    private static DeplacementTemporaire creer(SalleId salle, GenerateurId generateur, String motif) {
        return DeplacementTemporaire.creer(new DeplacementTemporaireId(UUID.randomUUID()), CENTRE, patient(1), DATE, salle, MATIN,
                generateur, motif, JeuDePlaces.ressources());
    }

    private static void refuse(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo("DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE");
    }

    @Test
    void should_create_a_move_to_an_in_service_generator_of_the_target_room_and_keep_the_reason() {
        DeplacementTemporaire d = creer(A, A2, "PREVENTIVE");
        assertThat(d.patient()).isEqualTo(patient(1));
        assertThat(d.date()).isEqualTo(DATE);
        assertThat(d.salle()).isEqualTo(A);
        assertThat(d.creneau()).isEqualTo(MATIN);
        assertThat(d.generateur()).isEqualTo(A2);
        assertThat(d.centre()).isEqualTo(CENTRE);
        assertThat(d.motif()).contains("PREVENTIVE");
    }

    @Test
    void should_accept_a_move_without_reason() {
        assertThat(creer(A, A2, null).motif()).isEmpty();
    }

    @Test
    void should_refuse_a_generator_that_belongs_to_another_room() {
        refuse(() -> creer(A, B1, null));
    }

    @Test
    void should_refuse_a_generator_that_is_not_in_service_or_unknown() {
        refuse(() -> creer(A, HORS_SERVICE, null));
        refuse(() -> creer(A, new GenerateurId(UUID.randomUUID()), null));
    }

    @Test
    void should_refuse_an_unknown_room_or_slot() {
        refuse(() -> creer(new SalleId(UUID.randomUUID()), A2, null));
        refuse(() -> DeplacementTemporaire.creer(new DeplacementTemporaireId(UUID.randomUUID()), CENTRE, patient(1), DATE, B,
                new Identifiants.CreneauId(UUID.randomUUID()), B1, null, JeuDePlaces.ressources()));
    }

    @Test
    void should_refuse_an_incomplete_target_or_identity() {
        assertThatThrownBy(() -> creer(null, A2, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> creer(A, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> DeplacementTemporaire.creer(null, CENTRE, patient(1), DATE, A, MATIN, A2, null,
                JeuDePlaces.ressources())).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> DeplacementTemporaire.creer(new DeplacementTemporaireId(UUID.randomUUID()), CENTRE, patient(1), null,
                A, MATIN, A2, null, JeuDePlaces.ressources())).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_read_back_an_inconsistent_stored_line_without_replaying_the_rules() {
        DeplacementTemporaire d = DeplacementTemporaire.reconstituer(new DeplacementTemporaireId(UUID.randomUUID()), CENTRE, patient(1),
                DATE, A, MATIN, B1, "ANCIEN");
        assertThat(d.generateur()).isEqualTo(B1);
    }
}
