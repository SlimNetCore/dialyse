package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.JEUDI;
import static com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.LUNDI;
import static com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.MARDI;
import static com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine.MERCREDI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariant P14 (RG-INF-020 à 022) : jamais deux affectations d'un infirmier sur le même créneau un même jour.
 */
class RoulementDeLInfirmierTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final InfirmierId INFIRMIER = new InfirmierId(UUID.randomUUID());
    private static final SalleId SALLE_A = new SalleId(UUID.randomUUID());
    private static final SalleId SALLE_B = new SalleId(UUID.randomUUID());
    private static final CreneauId MATIN = new CreneauId(UUID.randomUUID());
    private static final CreneauId SOIR = new CreneauId(UUID.randomUUID());

    private static AffectationId id() {
        return new AffectationId(UUID.randomUUID());
    }

    private static RoulementDeLInfirmier vide() {
        return RoulementDeLInfirmier.reconstituer(CENTRE, INFIRMIER, List.of());
    }

    private static void refuse(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo(code);
    }

    @Test
    void should_add_an_assignment_with_its_days_in_week_order() {
        AffectationId a = id();

        RoulementDeLInfirmier roulement = vide().affecter(a, SALLE_A, MATIN, EnumSet.of(MERCREDI, LUNDI));

        assertThat(roulement.affectation(a).orElseThrow().jours()).containsExactly(LUNDI, MERCREDI);
    }

    @Test
    void should_refuse_a_second_assignment_on_the_same_slot_the_same_day_even_in_another_room() {
        RoulementDeLInfirmier roulement = vide().affecter(id(), SALLE_A, MATIN, EnumSet.of(LUNDI, MARDI));

        refuse(() -> roulement.affecter(id(), SALLE_B, MATIN, EnumSet.of(MARDI, JEUDI)), "AFFECTATION_CHEVAUCHEMENT");
    }

    @Test
    void should_accept_the_same_slot_on_other_days_and_other_slots_on_the_same_days() {
        RoulementDeLInfirmier roulement = vide().affecter(id(), SALLE_A, MATIN, EnumSet.of(LUNDI))
                .affecter(id(), SALLE_B, MATIN, EnumSet.of(MARDI)).affecter(id(), SALLE_A, SOIR, EnumSet.of(LUNDI));

        assertThat(roulement.affectations()).hasSize(3);
    }

    @Test
    void should_require_at_least_one_working_day() {
        assertThatThrownBy(() -> vide().affecter(id(), SALLE_A, MATIN, Set.of())).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_modify_its_own_days_without_conflicting_with_itself() {
        AffectationId a = id();
        RoulementDeLInfirmier roulement = vide().affecter(a, SALLE_A, MATIN, EnumSet.of(LUNDI));

        RoulementDeLInfirmier modifie = roulement.modifier(a, SALLE_B, MATIN, EnumSet.of(LUNDI, MARDI));

        assertThat(modifie.affectation(a).orElseThrow().salle()).isEqualTo(SALLE_B);
        assertThat(modifie.affectation(a).orElseThrow().jours()).containsExactly(LUNDI, MARDI);
    }

    @Test
    void should_refuse_to_modify_an_assignment_so_that_it_overlaps_another_one() {
        AffectationId a = id();
        RoulementDeLInfirmier roulement = vide().affecter(a, SALLE_A, MATIN, EnumSet.of(LUNDI)).affecter(id(), SALLE_B, MATIN, EnumSet.of(MARDI));

        refuse(() -> roulement.modifier(a, SALLE_A, MATIN, EnumSet.of(LUNDI, MARDI)), "AFFECTATION_CHEVAUCHEMENT");
    }

    @Test
    void should_keep_inherited_overlapping_rows_removable_but_not_modifiable() {
        AffectationId a = id();
        AffectationId b = id();
        RoulementDeLInfirmier herite = RoulementDeLInfirmier.reconstituer(CENTRE, INFIRMIER, List.of(
                new Affectation(a, SALLE_A, MATIN, EnumSet.of(LUNDI)), new Affectation(b, SALLE_B, MATIN, EnumSet.of(LUNDI))));

        refuse(() -> herite.modifier(a, SALLE_A, MATIN, EnumSet.of(LUNDI)), "AFFECTATION_CHEVAUCHEMENT");
        assertThat(herite.retirer(a).affectations()).extracting(Affectation::id).containsExactly(b);
    }

    @Test
    void should_refuse_to_modify_or_remove_an_unknown_assignment() {
        refuse(() -> vide().modifier(id(), SALLE_A, MATIN, EnumSet.of(LUNDI)), "AFFECTATION_INTROUVABLE");
        refuse(() -> vide().retirer(id()), "AFFECTATION_INTROUVABLE");
    }

    @Test
    void should_tell_whether_the_nurse_is_planned_on_a_slot_or_on_a_case() {
        RoulementDeLInfirmier roulement = vide().affecter(id(), SALLE_A, MATIN, EnumSet.of(LUNDI));

        assertThat(roulement.prevuSurLeCreneau(MATIN, LUNDI)).isTrue();
        assertThat(roulement.prevuSurLeCreneau(MATIN, MARDI)).isFalse();
        assertThat(roulement.prevuSurLeCreneau(SOIR, LUNDI)).isFalse();
        assertThat(roulement.prevuSurLaCase(SALLE_A, MATIN, LUNDI)).isTrue();
        assertThat(roulement.prevuSurLaCase(SALLE_B, MATIN, LUNDI)).isFalse();
    }
}
