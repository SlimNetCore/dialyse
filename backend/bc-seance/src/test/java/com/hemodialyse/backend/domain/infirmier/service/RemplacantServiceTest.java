package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.Candidat;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.Presence.RaisonRemplacement;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.ISO;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.LUNDI;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.MATIN;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SALLE;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SALLE_B;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SOIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemplacantServiceTest {

    private static List<String> noms(List<Candidat> candidats) {
        return candidats.stream().map(Candidat::nom).toList();
    }

    @Test
    void absent_nurses_and_nurses_already_planned_on_the_slot_are_excluded() {
        PresenceTestData t = new PresenceTestData().patients(2, SALLE, MATIN, JourSemaine.LUNDI);
        InfirmierRef titulaire = t.infirmier("Amrani", false);
        InfirmierRef absent = t.infirmier("Benali", false);
        InfirmierRef ailleurs = t.infirmier("Cherif", false);
        InfirmierRef libre = t.infirmier("Djebbar", false);
        t.affecter(titulaire, SALLE, MATIN, JourSemaine.LUNDI)
                .affecter(ailleurs, SALLE_B, MATIN, JourSemaine.LUNDI)
                .absence(absent, LUNDI, LUNDI);

        List<Candidat> candidats = RemplacantService.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 10);

        assertEquals(List.of("Djebbar"), noms(candidats));
        assertTrue(candidats.get(0).raisons().contains(RaisonRemplacement.JOUR_LIBRE));
        assertEquals(libre.id(), candidats.get(0).infirmierId());
    }

    @Test
    void an_isolation_room_only_accepts_qualified_replacements() {
        PresenceTestData t = new PresenceTestData();
        t.infirmier("Amrani", false);
        t.infirmier("Benali", true);

        List<Candidat> candidats = RemplacantService.proposer(t.build(), LUNDI, ISO.id(), MATIN.id(), 10);

        assertEquals(List.of("Benali"), noms(candidats));
        assertTrue(candidats.get(0).raisons().contains(RaisonRemplacement.HABILITE_ISOLEMENT));
    }

    @Test
    void candidates_who_know_the_room_and_the_slot_rank_first() {
        PresenceTestData t = new PresenceTestData();
        InfirmierRef habitue = t.infirmier("Zahra", false);
        t.infirmier("Amine", false);
        t.affecter(habitue, SALLE, MATIN, JourSemaine.MARDI);

        List<Candidat> candidats = RemplacantService.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 10);

        assertEquals(List.of("Zahra", "Amine"), noms(candidats));
        assertTrue(candidats.get(0).raisons().containsAll(List.of(RaisonRemplacement.SALLE_CONNUE,
                RaisonRemplacement.CRENEAU_HABITUEL)));
        assertTrue(candidats.get(0).score() > candidats.get(1).score());
    }

    @Test
    void a_nurse_already_working_another_slot_the_same_day_is_penalised() {
        PresenceTestData t = new PresenceTestData();
        InfirmierRef doubleVacation = t.infirmier("Amine", false);
        t.infirmier("Zahra", false);
        t.affecter(doubleVacation, SALLE, SOIR, JourSemaine.LUNDI);

        List<Candidat> candidats = RemplacantService.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 10);

        assertEquals(List.of("Zahra", "Amine"), noms(candidats));
        assertTrue(candidats.get(1).raisons().contains(RaisonRemplacement.DOUBLE_VACATION));
        assertFalse(candidats.get(0).raisons().contains(RaisonRemplacement.DOUBLE_VACATION));
    }

    @Test
    void a_lighter_weekly_load_ranks_higher_and_the_list_is_limited() {
        PresenceTestData t = new PresenceTestData();
        InfirmierRef charge = t.infirmier("Amine", false);
        t.infirmier("Zahra", false);
        t.infirmier("Yacine", false);
        t.affecter(charge, SALLE_B, SOIR, JourSemaine.MARDI, JourSemaine.MERCREDI, JourSemaine.JEUDI,
                JourSemaine.VENDREDI);

        List<Candidat> tous = RemplacantService.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 10);
        List<Candidat> limites = RemplacantService.proposer(t.build(), LUNDI, SALLE.id(), MATIN.id(), 1);

        assertEquals("Amine", tous.get(2).nom());
        assertEquals(4, tous.get(2).seancesSemaine());
        assertEquals(1, limites.size());
    }
}
