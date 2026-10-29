package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.Presence.RemplacementPose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.infirmier;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.ISOLEMENT;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Politique de couverture (RG-PLN-088, 091 à 094, revue P8 M5) : éligibilité du remplaçant et choix du remplacé relié.
 */
class CouvertureDesAbsencesTest {

    private static final LocalDate LUNDI = JeuDOptimisation.LUNDI;

    private final List<InfirmierDeLaPresence> infirmiers = new ArrayList<>();
    private final List<AffectationPrevue> roulement = new ArrayList<>();
    private final List<AbsenceConnue> absences = new ArrayList<>();
    private final List<RemplacementPose> remplacements = new ArrayList<>();

    private static VacationPlanifiee vacation(int remplacant, Identifiants.SalleId salle, Identifiants.CreneauId creneau) {
        return new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, salle, creneau, infirmier(remplacant), "Remplaçant", false);
    }

    private static void refuse(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo("REMPLACEMENT_INELIGIBLE");
    }

    private void actif(int n, boolean habiliteIsolement) {
        infirmiers.add(new InfirmierDeLaPresence(infirmier(n).valeur(), "Nom" + n, "Prenom" + n, Qualification.INFIRMIER,
                habiliteIsolement));
    }

    private void prevuAuRoulement(int n, Identifiants.SalleId salle) {
        roulement.add(new AffectationPrevue(infirmier(n).valeur(), salle.valeur(), MATIN.valeur(),
                EnumSet.of(JourDeLaSemaine.LUNDI)));
    }

    private void absentLeLundi(int n) {
        absences.add(new AbsenceConnue(infirmier(n).valeur(), LUNDI, LUNDI, TypeAbsence.CONGE));
    }

    private CouvertureDesAbsences couverture() {
        return new CouvertureDesAbsences(new Personnel(infirmiers, roulement, absences, remplacements, new RatioDeLaPresence(4, false)),
                JeuDePlaces.ressources());
    }

    @Test
    void should_link_the_absent_nurse_normally_planned_on_the_case_as_the_replaced() {
        actif(1, false);
        actif(2, false);
        prevuAuRoulement(1, A);
        absentLeLundi(1);

        var affectation = couverture().affecter(vacation(2, A, MATIN));

        assertThat(affectation.remplacant().infirmier()).isEqualTo(infirmier(2));
        assertThat(affectation.remplace()).isEqualTo(infirmier(1));
    }

    @Test
    void should_cover_without_replaced_when_nobody_planned_on_the_case_is_absent() {
        actif(1, false);
        actif(2, false);
        prevuAuRoulement(1, A);
        absentLeLundi(2); // absent mais pas prévu sur la case : le remplaçant ne peut pas être absent

        assertThatThrownBy(() -> couverture().affecter(vacation(2, A, MATIN)))
                .isInstanceOf(RegleDePlanificationViolee.class).extracting(e -> ((RegleDePlanificationViolee) e).code())
                .isEqualTo("REMPLACEMENT_INELIGIBLE");

        absences.clear();
        assertThat(couverture().affecter(vacation(2, A, MATIN)).remplace()).isNull();
    }

    @Test
    void should_replace_each_absent_nurse_of_a_case_once_in_identifier_order() {
        actif(1, false);
        actif(2, false);
        actif(3, false);
        actif(4, false);
        prevuAuRoulement(2, A);
        prevuAuRoulement(1, A);
        absentLeLundi(1);
        absentLeLundi(2);
        CouvertureDesAbsences couverture = couverture();

        assertThat(couverture.affecter(vacation(3, A, MATIN)).remplace()).isEqualTo(infirmier(1));
        assertThat(couverture.affecter(vacation(4, A, MATIN)).remplace()).isEqualTo(infirmier(2));
        assertThat(couverture().remplacesPossibles(vacation(3, A, MATIN))).containsExactly(infirmier(1), infirmier(2));
    }

    @Test
    void should_refuse_a_replacement_that_is_unknown_absent_already_planned_or_not_qualified_for_isolation() {
        actif(1, false);
        actif(2, false);
        prevuAuRoulement(2, A);
        // inconnu du centre
        refuse(() -> couverture().affecter(vacation(9, A, MATIN)));
        // déjà prévu sur le créneau (au roulement), même dans une autre salle
        refuse(() -> couverture().affecter(vacation(2, B, MATIN)));
        // libre sur un autre créneau : éligible
        assertThat(couverture().affecter(vacation(2, B, SOIR)).remplacant().infirmier()).isEqualTo(infirmier(2));
        // salle d'isolement sans habilitation
        refuse(() -> couverture().affecter(vacation(1, ISOLEMENT, MATIN)));
        // déjà remplaçant sur le créneau ce jour-là
        remplacements.add(new RemplacementPose(java.util.UUID.randomUUID(), LUNDI, B.valeur(), SOIR.valeur(), infirmier(1).valeur()));
        refuse(() -> couverture().affecter(vacation(1, A, SOIR)));
    }

    @Test
    void should_accept_an_isolation_room_for_a_qualified_nurse() {
        actif(1, true);
        assertThat(couverture().affecter(vacation(1, ISOLEMENT, MATIN)).remplacant().infirmier()).isEqualTo(infirmier(1));
    }
}
