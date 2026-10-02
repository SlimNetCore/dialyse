package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.StatutCase;
import com.hemodialyse.backend.domain.infirmier.model.Presence.TypeConflit;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.DIMANCHE;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.ISO;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.LUNDI;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.MARDI;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.MATIN;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SALLE;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SALLE_B;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SOIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceInfirmierServiceTest {

    private static CasePresence cas(SemainePresence s, SalleRef salle, CreneauRef creneau, JourSemaine jour) {
        return s.cases().stream()
                .filter(c -> c.salleId().equals(salle.id()) && c.creneauId().equals(creneau.id()) && c.jour() == jour)
                .findFirst().orElseThrow();
    }

    @Test
    void the_required_staff_is_the_patient_count_over_the_ratio_rounded_up() {
        assertEquals(0, PresenceInfirmierService.requis(0, 4));
        assertEquals(1, PresenceInfirmierService.requis(1, 4));
        assertEquals(1, PresenceInfirmierService.requis(4, 4));
        assertEquals(2, PresenceInfirmierService.requis(5, 4));
        assertEquals(3, PresenceInfirmierService.requis(9, 4));
    }

    @Test
    void a_slot_is_understaffed_until_enough_nurses_are_planned() {
        PresenceTestData t = new PresenceTestData().patients(5, SALLE, MATIN, JourSemaine.LUNDI);
        InfirmierRef a = t.infirmier("Amrani", false);
        InfirmierRef b = t.infirmier("Benali", false);
        t.affecter(a, SALLE, MATIN, JourSemaine.LUNDI);

        CasePresence seul = cas(PresenceInfirmierService.construire(t.build(), DIMANCHE), SALLE, MATIN, JourSemaine.LUNDI);
        assertEquals(StatutCase.SOUS_EFFECTIF, seul.statut());
        assertEquals(2, seul.requis());
        assertEquals(1, seul.manque());
        assertEquals(5, seul.patients());

        t.affecter(b, SALLE, MATIN, JourSemaine.LUNDI);
        CasePresence deux = cas(PresenceInfirmierService.construire(t.build(), DIMANCHE), SALLE, MATIN, JourSemaine.LUNDI);
        assertEquals(StatutCase.COUVERT, deux.statut());
        assertEquals(List.of("Amrani", "Benali"), deux.presents().stream().map(p -> p.nom()).toList());
    }

    @Test
    void the_ratio_of_the_center_drives_the_requirement() {
        PresenceTestData t = new PresenceTestData().ratio(2).patients(5, SALLE, MATIN, JourSemaine.LUNDI);

        CasePresence c = cas(PresenceInfirmierService.construire(t.build(), DIMANCHE), SALLE, MATIN, JourSemaine.LUNDI);

        assertEquals(3, c.requis());
        assertEquals(3, c.manque());
    }

    @Test
    void a_slot_without_patient_needs_no_nurse() {
        PresenceTestData t = new PresenceTestData();
        t.affecter(t.infirmier("Amrani", false), SALLE, MATIN, JourSemaine.LUNDI);

        SemainePresence s = PresenceInfirmierService.construire(t.build(), DIMANCHE);

        assertEquals(StatutCase.SANS_PATIENT, cas(s, SALLE, MATIN, JourSemaine.LUNDI).statut());
        assertEquals(StatutCase.SANS_PATIENT, cas(s, SALLE, SOIR, JourSemaine.MARDI).statut());
        assertEquals(0, s.casesSousEffectif());
    }

    @Test
    void closed_days_need_no_staff_whether_weekly_or_dated() {
        PresenceTestData t = new PresenceTestData()
                .joursOuverts(EnumSet.complementOf(EnumSet.of(JourSemaine.VENDREDI)))
                .fermeture(MARDI)
                .patients(4, SALLE, MATIN, JourSemaine.LUNDI, JourSemaine.MARDI, JourSemaine.VENDREDI);

        SemainePresence s = PresenceInfirmierService.construire(t.build(), DIMANCHE);

        assertEquals(StatutCase.SOUS_EFFECTIF, cas(s, SALLE, MATIN, JourSemaine.LUNDI).statut());
        assertEquals(StatutCase.FERME, cas(s, SALLE, MATIN, JourSemaine.MARDI).statut());
        assertEquals(StatutCase.FERME, cas(s, SALLE, MATIN, JourSemaine.VENDREDI).statut());
        assertEquals(1, s.casesSousEffectif());
    }

    @Test
    void an_absence_removes_the_nurse_and_a_replacement_covers_the_slot() {
        PresenceTestData t = new PresenceTestData().patients(3, SALLE, MATIN, JourSemaine.LUNDI);
        InfirmierRef titulaire = t.infirmier("Amrani", false);
        InfirmierRef renfort = t.infirmier("Benali", false);
        t.affecter(titulaire, SALLE, MATIN, JourSemaine.LUNDI).absence(titulaire, LUNDI, LUNDI);

        CasePresence absente = cas(PresenceInfirmierService.construire(t.build(), DIMANCHE), SALLE, MATIN, JourSemaine.LUNDI);
        assertEquals(StatutCase.SOUS_EFFECTIF, absente.statut());
        assertEquals(List.of("Amrani"), absente.absents().stream().map(a -> a.nom()).toList());
        assertTrue(absente.presents().isEmpty());

        RemplacementInfirmier r = t.remplacer(LUNDI, SALLE, MATIN, renfort);
        CasePresence couverte = cas(PresenceInfirmierService.construire(t.build(), DIMANCHE), SALLE, MATIN, JourSemaine.LUNDI);
        assertEquals(StatutCase.COUVERT, couverte.statut());
        assertTrue(couverte.presents().get(0).remplacant());
        assertEquals(r.id(), couverte.presents().get(0).remplacementId());
    }

    @Test
    void an_absence_only_affects_the_days_it_covers() {
        PresenceTestData t = new PresenceTestData().patients(2, SALLE, MATIN, JourSemaine.LUNDI, JourSemaine.MARDI);
        InfirmierRef a = t.infirmier("Amrani", false);
        t.affecter(a, SALLE, MATIN, JourSemaine.LUNDI, JourSemaine.MARDI).absence(a, LUNDI, LUNDI);

        SemainePresence s = PresenceInfirmierService.construire(t.build(), DIMANCHE);

        assertEquals(StatutCase.SOUS_EFFECTIF, cas(s, SALLE, MATIN, JourSemaine.LUNDI).statut());
        assertEquals(StatutCase.COUVERT, cas(s, SALLE, MATIN, JourSemaine.MARDI).statut());
    }

    @Test
    void only_qualified_nurses_count_in_an_isolation_room_and_others_are_flagged() {
        PresenceTestData t = new PresenceTestData().patients(2, ISO, MATIN, JourSemaine.LUNDI);
        InfirmierRef nonHabilite = t.infirmier("Amrani", false);
        t.affecter(nonHabilite, ISO, MATIN, JourSemaine.LUNDI);

        SemainePresence s = PresenceInfirmierService.construire(t.build(), DIMANCHE);

        CasePresence c = cas(s, ISO, MATIN, JourSemaine.LUNDI);
        assertTrue(c.salleIsolement());
        assertEquals(StatutCase.SOUS_EFFECTIF, c.statut());
        assertTrue(s.conflits().stream().anyMatch(x -> x.type() == TypeConflit.NON_HABILITE && x.infirmier().equals("Amrani")));

        t.affecter(t.infirmier("Benali", true), ISO, MATIN, JourSemaine.LUNDI);
        assertEquals(StatutCase.COUVERT,
                cas(PresenceInfirmierService.construire(t.build(), DIMANCHE), ISO, MATIN, JourSemaine.LUNDI).statut());
    }

    @Test
    void a_nurse_planned_in_two_rooms_on_the_same_slot_is_a_conflict() {
        PresenceTestData t = new PresenceTestData();
        InfirmierRef a = t.infirmier("Amrani", false);
        t.affecter(a, SALLE, MATIN, JourSemaine.LUNDI).affecter(a, SALLE_B, MATIN, JourSemaine.LUNDI);

        SemainePresence s = PresenceInfirmierService.construire(t.build(), DIMANCHE);

        assertEquals(1, s.conflits().stream().filter(c -> c.type() == TypeConflit.DOUBLE_AFFECTATION).count());
    }

    @Test
    void a_nurse_gets_his_own_slots_as_planned_replacement_or_absent_in_chronological_order() {
        PresenceTestData t = new PresenceTestData();
        InfirmierRef moi = t.infirmier("Amrani", false);
        InfirmierRef autre = t.infirmier("Benali", false);
        t.affecter(moi, SALLE, MATIN, JourSemaine.LUNDI, JourSemaine.MARDI)
                .affecter(moi, SALLE, SOIR, JourSemaine.LUNDI)
                .affecter(autre, SALLE, MATIN, JourSemaine.MERCREDI)
                .absence(moi, MARDI, MARDI);
        t.remplacer(DIMANCHE.plusDays(3), SALLE_B, MATIN, moi);
        SemainePresence s = PresenceInfirmierService.construire(t.build(), DIMANCHE);

        var creneaux = PresenceInfirmierService.creneauxDe(s, moi.id());

        assertEquals(4, creneaux.size());
        assertEquals(LUNDI, creneaux.get(0).date());
        assertEquals(MATIN.id(), creneaux.get(0).creneauId());
        assertEquals(SOIR.id(), creneaux.get(1).creneauId());
        assertEquals(MARDI, creneaux.get(2).date());
        assertEquals(com.hemodialyse.backend.domain.infirmier.model.Presence.SituationPersonnelle.ABSENT,
                creneaux.get(2).situation());
        assertEquals(com.hemodialyse.backend.domain.infirmier.model.Presence.SituationPersonnelle.REMPLACANT,
                creneaux.get(3).situation());
        assertTrue(PresenceInfirmierService.creneauxDe(s, UUID.randomUUID()).isEmpty());
    }

    @Test
    void alerts_list_understaffed_slots_inside_the_window_in_chronological_order() {
        PresenceTestData t = new PresenceTestData()
                .patients(2, SALLE, SOIR, JourSemaine.LUNDI)
                .patients(2, SALLE, MATIN, JourSemaine.LUNDI, JourSemaine.MARDI);
        InfirmierRef a = t.infirmier("Amrani", false);
        t.affecter(a, SALLE, MATIN, JourSemaine.MARDI).affecter(a, SALLE, SOIR, JourSemaine.LUNDI);
        t.absence(a, MARDI, MARDI);

        List<AlertePresence> alertes = PresenceInfirmierService.alertes(t.build(), LUNDI, LUNDI.plusDays(1));

        // lundi matin : personne de prévu ; mardi matin : titulaire absent ; lundi soir : couvert
        assertEquals(2, alertes.size());
        assertEquals(LUNDI, alertes.get(0).date());
        assertEquals(MATIN.id(), alertes.get(0).creneauId());
        assertEquals(MARDI, alertes.get(1).date());
        assertEquals(List.of("Amrani"), alertes.get(1).absents());
        assertFalse(alertes.stream().anyMatch(x -> x.creneauId().equals(SOIR.id())));
        assertTrue(PresenceInfirmierService.alertes(t.build(), LUNDI.plusDays(2), LUNDI.plusDays(3)).isEmpty());
    }
}
