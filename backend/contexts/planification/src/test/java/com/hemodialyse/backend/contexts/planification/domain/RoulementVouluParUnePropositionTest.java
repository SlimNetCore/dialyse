package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier.CaseVoulue;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier.Changements;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.infirmier;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P22 / P14 : le roulement d'une proposition est JUGÉ par l'agrégat (jamais deux salles au même créneau un même jour) et seules les
 * lignes touchées changent : identiques conservées, autres modifiées, créées ou supprimées.
 */
class RoulementVouluParUnePropositionTest {

    private static final Set<JourDeLaSemaine> LMV = EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MERCREDI, JourDeLaSemaine.VENDREDI);
    private static final Set<JourDeLaSemaine> MJ = EnumSet.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.JEUDI);
    private static final AffectationId EXISTANTE_1 = new AffectationId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
    private static final AffectationId EXISTANTE_2 = new AffectationId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"));

    private final AtomicInteger sequence = new AtomicInteger(100);

    private AffectationId nouvelle() {
        return new AffectationId(new UUID(7, sequence.incrementAndGet()));
    }

    private static RoulementDeLInfirmier roulement(Affectation... affectations) {
        return RoulementDeLInfirmier.reconstituer(CENTRE, infirmier(1), List.of(affectations));
    }

    private static Map<CaseVoulue, Set<JourDeLaSemaine>> voulu(Object... paires) {
        Map<CaseVoulue, Set<JourDeLaSemaine>> v = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) v.put((CaseVoulue) paires[i], (Set<JourDeLaSemaine>) paires[i + 1]);
        return v;
    }

    @Test
    void should_keep_identical_assignments_untouched() {
        RoulementDeLInfirmier actuel = roulement(new Affectation(EXISTANTE_1, A, MATIN, LMV));

        Changements c = actuel.remplacerPar(voulu(new CaseVoulue(A, MATIN), LMV), this::nouvelle);

        assertThat(c.estVide()).isTrue();
        assertThat(c.suivant().affectations()).containsExactlyElementsOf(actuel.affectations());
    }

    @Test
    void should_modify_the_days_of_an_assignment_that_stays_on_the_same_room_and_slot() {
        RoulementDeLInfirmier actuel = roulement(new Affectation(EXISTANTE_1, A, MATIN, LMV));

        Changements c = actuel.remplacerPar(voulu(new CaseVoulue(A, MATIN), MJ), this::nouvelle);

        assertThat(c.modifiees()).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(EXISTANTE_1);
            assertThat(a.jours()).isEqualTo(MJ);
        });
        assertThat(c.ajoutees()).isEmpty();
        assertThat(c.retirees()).isEmpty();
    }

    @Test
    void should_remove_the_assignments_the_proposition_does_not_want_and_create_the_new_ones() {
        RoulementDeLInfirmier actuel = roulement(new Affectation(EXISTANTE_1, A, MATIN, LMV));

        Changements c = actuel.remplacerPar(voulu(new CaseVoulue(B, SOIR), MJ), this::nouvelle);

        assertThat(c.retirees()).containsExactly(EXISTANTE_1);
        assertThat(c.ajoutees()).singleElement().satisfies(a -> {
            assertThat(a.salle()).isEqualTo(B);
            assertThat(a.creneau()).isEqualTo(SOIR);
            assertThat(a.jours()).isEqualTo(MJ);
        });
        assertThat(c.suivant().affectations()).hasSize(1);
    }

    @Test
    void should_remove_everything_when_the_proposition_wants_nothing_for_this_nurse() {
        RoulementDeLInfirmier actuel = roulement(new Affectation(EXISTANTE_1, A, MATIN, LMV), new Affectation(EXISTANTE_2, B, SOIR, MJ));

        Changements c = actuel.remplacerPar(Map.of(), this::nouvelle);

        assertThat(c.retirees()).containsExactly(EXISTANTE_1, EXISTANTE_2);
        assertThat(c.suivant().affectations()).isEmpty();
    }

    @Test
    void should_collapse_a_duplicate_of_the_same_room_and_slot() {
        RoulementDeLInfirmier actuel = roulement(new Affectation(EXISTANTE_1, A, MATIN, MJ), new Affectation(EXISTANTE_2, A, MATIN, LMV));

        Changements c = actuel.remplacerPar(voulu(new CaseVoulue(A, MATIN), MJ), this::nouvelle);

        assertThat(c.retirees()).containsExactly(EXISTANTE_2);
        assertThat(c.modifiees()).isEmpty();
    }

    @Test
    void should_judge_the_wanted_roulement_and_refuse_two_rooms_on_the_same_slot_the_same_day() {
        RoulementDeLInfirmier actuel = roulement();

        assertThatThrownBy(() -> actuel.remplacerPar(
                voulu(new CaseVoulue(A, MATIN), LMV, new CaseVoulue(B, MATIN), EnumSet.of(JourDeLaSemaine.LUNDI)), this::nouvelle))
                .isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo("AFFECTATION_CHEVAUCHEMENT");
    }

    @Test
    void should_accept_two_rooms_on_the_same_slot_on_different_days_and_the_same_room_on_two_slots() {
        RoulementDeLInfirmier actuel = roulement();

        Changements c = actuel.remplacerPar(voulu(new CaseVoulue(A, MATIN), LMV, new CaseVoulue(B, MATIN), MJ,
                new CaseVoulue(A, SOIR), LMV), this::nouvelle);

        assertThat(c.ajoutees()).hasSize(3);
    }

    @Test
    void should_ignore_a_wanted_case_without_any_day() {
        Changements c = roulement().remplacerPar(voulu(new CaseVoulue(A, MATIN), Set.of()), this::nouvelle);
        assertThat(c.estVide()).isTrue();
    }
}
