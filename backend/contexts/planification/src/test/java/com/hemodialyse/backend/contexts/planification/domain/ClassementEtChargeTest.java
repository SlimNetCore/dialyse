package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.Candidat;
import com.hemodialyse.backend.contexts.planification.domain.Presence.ChargeInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.RaisonDeRemplacement;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.ISO;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.LUNDI;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.SALLE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.SALLE_B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.SOIR;
import static org.assertj.core.api.Assertions.assertThat;

class ClassementEtChargeTest {

    @Test
    void should_exclude_absent_nurses_those_already_planned_on_the_slot_and_unqualified_ones_in_isolation() {
        JeuDePresence t = new JeuDePresence();
        InfirmierDeLaPresence absent = t.infirmier("Absent", true);
        InfirmierDeLaPresence prevu = t.infirmier("Prevu", true);
        InfirmierDeLaPresence libre = t.infirmier("Libre", true);
        t.infirmier("Standard", false);
        t.absence(absent, LUNDI, LUNDI).affecter(prevu, SALLE_B, MATIN, JourDeLaSemaine.LUNDI);

        assertThat(ClassementDesRemplacants.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 10)).extracting(Candidat::nom)
                .containsExactlyInAnyOrder("Libre", "Standard");
        assertThat(ClassementDesRemplacants.proposer(t.build(), LUNDI, ISO.id(), MATIN.id(), 10)).extracting(Candidat::nom)
                .containsExactly("Libre");
        assertThat(libre.habiliteIsolement()).isTrue();
    }

    @Test
    void should_score_known_room_usual_slot_free_day_and_penalize_a_double_vacation() {
        JeuDePresence t = new JeuDePresence();
        InfirmierDeLaPresence connue = t.infirmier("Connue", false);
        InfirmierDeLaPresence soir = t.infirmier("Soir", false);
        t.affecter(connue, SALLE, MATIN, JourDeLaSemaine.MARDI).affecter(soir, SALLE_B, SOIR, JourDeLaSemaine.LUNDI);

        List<Candidat> candidats = ClassementDesRemplacants.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 10);

        Candidat c = candidats.getFirst();
        assertThat(c.nom()).isEqualTo("Connue");
        assertThat(c.raisons()).containsExactly(RaisonDeRemplacement.SALLE_CONNUE, RaisonDeRemplacement.CRENEAU_HABITUEL,
                RaisonDeRemplacement.CHARGE_FAIBLE, RaisonDeRemplacement.JOUR_LIBRE);
        assertThat(c.score()).isEqualTo(40 + 15 + 10 + 16 + 10);
        Candidat s = candidats.get(1);
        assertThat(s.raisons()).contains(RaisonDeRemplacement.DOUBLE_VACATION).doesNotContain(RaisonDeRemplacement.JOUR_LIBRE);
        assertThat(s.score()).isEqualTo(40 + 16 - 25);
    }

    @Test
    void should_limit_the_candidates_and_break_ties_by_name() {
        JeuDePresence t = new JeuDePresence();
        t.infirmier("Zebda", false);
        t.infirmier("Amrani", false);
        t.infirmier("Benali", false);
        List<Candidat> un = ClassementDesRemplacants.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 1);
        assertThat(un).extracting(Candidat::nom).containsExactly("Amrani");
    }

    @Test
    void should_count_rotation_sessions_on_open_days_without_absences_and_replacements() {
        JeuDePresence t = new JeuDePresence().ouverts(JourDeLaSemaine.LUNDI);
        InfirmierDeLaPresence a = t.infirmier("Amrani", "Sara", false);
        t.affecter(a, SALLE, MATIN, JourDeLaSemaine.LUNDI);
        YearMonth novembre = YearMonth.of(2026, 11);   // 4 lundis : 2, 9, 16, 23, 30 = 5 lundis
        ChargeInfirmier c = ChargeDesInfirmiers.mensuelle(t.build(), novembre).infirmiers().getFirst();
        assertThat(c.seances()).isEqualTo(5);
        t.absence(a, java.time.LocalDate.of(2026, 11, 9), java.time.LocalDate.of(2026, 11, 10));
        ChargeInfirmier absente = ChargeDesInfirmiers.mensuelle(t.build(), novembre).infirmiers().getFirst();
        assertThat(absente.seances()).isEqualTo(4);
        assertThat(absente.joursAbsence()).isEqualTo(2);
    }

    @Test
    void should_ignore_replacements_on_absence_or_closed_days_and_sort_by_name_then_first_name() {
        JeuDePresence t = new JeuDePresence().ouverts(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        InfirmierDeLaPresence zebda = t.infirmier("Zebda", "Alia", false);
        InfirmierDeLaPresence amrani = t.infirmier("Amrani", "Sara", false);
        InfirmierDeLaPresence amraniKarim = t.infirmier("Amrani", "Karim", false);
        java.time.LocalDate lundi = java.time.LocalDate.of(2026, 11, 2);
        t.remplacement(zebda, lundi, SALLE, MATIN)                                   // compté
                .remplacement(zebda, lundi.plusDays(2), SALLE, MATIN)                // mercredi fermé
                .remplacement(amrani, lundi.plusDays(7), SALLE, MATIN).absence(amrani, lundi.plusDays(7), lundi.plusDays(7));

        var charge = ChargeDesInfirmiers.mensuelle(t.build(), YearMonth.of(2026, 11));

        assertThat(charge.infirmiers()).extracting(ChargeInfirmier::nom).containsExactly("Karim Amrani", "Sara Amrani", "Alia Zebda");
        assertThat(charge.infirmiers().get(2).remplacements()).isEqualTo(1);
        assertThat(charge.infirmiers().get(1).remplacements()).isZero();
        assertThat(amraniKarim.id()).isNotNull();
        assertThat(charge.moyenne()).isEqualTo(Math.round(1 / 3.0 * 10) / 10.0);
    }
}
