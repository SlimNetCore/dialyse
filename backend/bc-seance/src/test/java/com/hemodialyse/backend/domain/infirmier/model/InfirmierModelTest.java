package com.hemodialyse.backend.domain.infirmier.model;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfirmierModelTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID SALLE = UUID.randomUUID();
    private static final UUID MATIN = UUID.randomUUID();
    private static final UUID SOIR = UUID.randomUUID();

    @Test
    void an_infirmier_requires_a_matricule_a_name_and_a_qualification() {
        assertThrows(IllegalArgumentException.class,
                () -> Infirmier.creer(CENTRE, " ", "Amrani", null, null, QualificationInfirmier.INFIRMIER, false));
        assertThrows(IllegalArgumentException.class,
                () -> Infirmier.creer(CENTRE, "M1", "", null, null, QualificationInfirmier.INFIRMIER, false));
        assertThrows(IllegalArgumentException.class, () -> Infirmier.creer(CENTRE, "M1", "Amrani", null, null, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> Infirmier.creer(null, "M1", "Amrani", null, null, QualificationInfirmier.INFIRMIER, false));
    }

    @Test
    void an_infirmier_is_trimmed_active_when_created_and_can_be_deactivated_and_reactivated() {
        Infirmier i = Infirmier.creer(CENTRE, " M1 ", " Amrani ", " ", "", QualificationInfirmier.MAJOR, true);

        assertEquals("M1", i.matricule());
        assertEquals("Amrani", i.nomComplet());
        assertNull(i.prenom());
        assertNull(i.telephone());
        assertTrue(i.actif());
        assertFalse(i.desactiver().actif());
        assertTrue(i.desactiver().reactiver().actif());
        assertEquals("Sara Amrani", i.modifier("M1", "Amrani", "Sara", null, QualificationInfirmier.MAJOR, true).nomComplet());
    }

    @Test
    void an_account_can_be_linked_once_kept_through_updates_and_unlinked() {
        UUID compte = UUID.randomUUID();
        Infirmier sans = Infirmier.creer(CENTRE, "M1", "Amrani", null, null, QualificationInfirmier.INFIRMIER, false);

        Infirmier lie = sans.lierCompte(compte);

        assertNull(sans.userId());
        assertEquals(compte, lie.userId());
        assertEquals(compte, lie.modifier("M1", "Amrani", "Sara", null, QualificationInfirmier.MAJOR, true).userId());
        assertEquals(compte, lie.desactiver().userId());
        assertEquals(compte, lie.desactiver().reactiver().userId());
        assertNull(lie.delierCompte().userId());
        assertThrows(IllegalStateException.class, () -> lie.lierCompte(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> sans.lierCompte(null));
    }

    @Test
    void a_nurse_can_only_cancel_an_absence_that_has_not_started() {
        LocalDate aujourdhui = LocalDate.of(2026, 10, 5);
        UUID infirmier = UUID.randomUUID();

        AbsenceInfirmier future = AbsenceInfirmier.creer(CENTRE, infirmier, aujourdhui.plusDays(1), aujourdhui.plusDays(3),
                TypeAbsence.CONGE, null);
        AbsenceInfirmier commencee = AbsenceInfirmier.creer(CENTRE, infirmier, aujourdhui, aujourdhui.plusDays(3),
                TypeAbsence.CONGE, null);
        AbsenceInfirmier passee = AbsenceInfirmier.creer(CENTRE, infirmier, aujourdhui.minusDays(5), aujourdhui.minusDays(2),
                TypeAbsence.MALADIE, null);

        assertTrue(future.annulableParInfirmier(aujourdhui));
        assertFalse(commencee.annulableParInfirmier(aujourdhui));
        assertFalse(passee.annulableParInfirmier(aujourdhui));
    }

    @Test
    void an_assignment_needs_at_least_one_day_and_detects_overlaps_on_the_same_slot() {
        UUID infirmier = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> AffectationInfirmier.creer(CENTRE, infirmier, SALLE, MATIN, Set.of()));

        AffectationInfirmier lmv = AffectationInfirmier.creer(CENTRE, infirmier, SALLE, MATIN,
                EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI));
        AffectationInfirmier lundiMemeCreneau = AffectationInfirmier.creer(CENTRE, infirmier, UUID.randomUUID(), MATIN,
                EnumSet.of(JourSemaine.LUNDI));
        AffectationInfirmier lundiSoir = AffectationInfirmier.creer(CENTRE, infirmier, SALLE, SOIR,
                EnumSet.of(JourSemaine.LUNDI));
        AffectationInfirmier autreInfirmier = AffectationInfirmier.creer(CENTRE, UUID.randomUUID(), SALLE, MATIN,
                EnumSet.of(JourSemaine.LUNDI));

        assertTrue(lmv.chevauche(lundiMemeCreneau));
        assertFalse(lmv.chevauche(lundiSoir));
        assertFalse(lmv.chevauche(autreInfirmier));
        assertFalse(lmv.chevauche(lmv), "une affectation ne chevauche pas elle-même (cas de la modification)");
    }

    @Test
    void an_absence_covers_its_bounds_and_rejects_invalid_periods() {
        UUID infirmier = UUID.randomUUID();
        LocalDate debut = LocalDate.of(2026, 10, 5);
        AbsenceInfirmier a = AbsenceInfirmier.creer(CENTRE, infirmier, debut, debut.plusDays(2), TypeAbsence.CONGE, " ");

        assertTrue(a.couvre(debut));
        assertTrue(a.couvre(debut.plusDays(2)));
        assertFalse(a.couvre(debut.minusDays(1)));
        assertFalse(a.couvre(debut.plusDays(3)));
        assertNull(a.motif());
        assertThrows(IllegalArgumentException.class,
                () -> AbsenceInfirmier.creer(CENTRE, infirmier, debut, debut.minusDays(1), TypeAbsence.CONGE, null));
        assertThrows(IllegalArgumentException.class,
                () -> AbsenceInfirmier.creer(CENTRE, infirmier, debut, debut.plusDays(400), TypeAbsence.CONGE, null));
        assertThrows(IllegalArgumentException.class,
                () -> AbsenceInfirmier.creer(CENTRE, infirmier, debut, debut, null, null));
    }

    @Test
    void a_replacement_cannot_replace_the_same_person() {
        UUID infirmier = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> RemplacementInfirmier.creer(CENTRE,
                LocalDate.of(2026, 10, 5), SALLE, MATIN, infirmier, infirmier));
        assertEquals(infirmier, RemplacementInfirmier.creer(CENTRE, LocalDate.of(2026, 10, 5), SALLE, MATIN, infirmier,
                null).infirmierId());
    }
}
