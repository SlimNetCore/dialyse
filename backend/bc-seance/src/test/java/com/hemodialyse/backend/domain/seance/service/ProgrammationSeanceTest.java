package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.SituationPlanning;
import com.hemodialyse.backend.domain.seance.service.ProgrammationSeance.RaisonHorsPlanning;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammationSeanceTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 10, 5);

    private static SituationPlanning regulier() {
        return new SituationPlanning(false, true, false, null, null, LocalDate.of(2025, 1, 1));
    }

    private static SituationPlanning etat(String etat, LocalDate evenement) {
        return new SituationPlanning(false, true, false, etat, evenement, LocalDate.of(2025, 1, 1));
    }

    @Test
    void un_patient_regulier_un_jour_de_dialyse_est_attendu() {
        assertTrue(ProgrammationSeance.evaluer(regulier(), JOUR).isEmpty());
    }

    @Test
    void le_centre_ferme_prime_sur_tout_le_reste() {
        var s = new SituationPlanning(true, false, true, null, null, null);
        assertEquals(Optional.of(RaisonHorsPlanning.CENTRE_FERME), ProgrammationSeance.evaluer(s, JOUR));
    }

    @Test
    void hors_des_jours_de_dialyse_le_patient_n_est_pas_programme() {
        var s = new SituationPlanning(false, false, false, null, null, null);
        assertEquals(Optional.of(RaisonHorsPlanning.JOUR_NON_DIALYSE), ProgrammationSeance.evaluer(s, JOUR));
    }

    @Test
    void sommeil_ou_admission_future_rendent_le_patient_non_attendu() {
        var sommeil = new SituationPlanning(false, true, true, null, null, null);
        var futur = new SituationPlanning(false, true, false, null, null, JOUR.plusDays(1));

        assertEquals(Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU), ProgrammationSeance.evaluer(sommeil, JOUR));
        assertEquals(Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU), ProgrammationSeance.evaluer(futur, JOUR));
    }

    @Test
    void un_deces_ou_une_greffe_ne_sont_plus_attendus_a_partir_de_la_date_de_l_evenement() {
        for (String etat : new String[]{"DECEDE", "GREFFE"}) {
            assertTrue(ProgrammationSeance.evaluer(etat(etat, JOUR.plusDays(1)), JOUR).isEmpty(), etat + " veille");
            assertEquals(Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU),
                    ProgrammationSeance.evaluer(etat(etat, JOUR), JOUR), etat + " jour même");
            assertEquals(Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU),
                    ProgrammationSeance.evaluer(etat(etat, null), JOUR), etat + " sans date");
        }
    }

    @Test
    void un_transfert_ou_une_guerison_restent_attendus_jusqu_au_jour_de_sortie_inclus() {
        for (String etat : new String[]{"TRANSFERE", "GUERRI"}) {
            assertTrue(ProgrammationSeance.evaluer(etat(etat, JOUR), JOUR).isEmpty(), etat + " jour de sortie");
            assertEquals(Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU),
                    ProgrammationSeance.evaluer(etat(etat, JOUR.minusDays(1)), JOUR), etat + " après la sortie");
        }
    }

    @Test
    void un_sejour_limite_est_attendu_jusqu_a_la_fin_du_sejour_incluse() {
        for (String etat : new String[]{"OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER"}) {
            assertTrue(ProgrammationSeance.evaluer(etat(etat, JOUR), JOUR).isEmpty(), etat + " dernier jour");
            assertTrue(ProgrammationSeance.evaluer(etat(etat, null), JOUR).isEmpty(), etat + " sans fin");
            assertEquals(Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU),
                    ProgrammationSeance.evaluer(etat(etat, JOUR.minusDays(1)), JOUR), etat + " séjour terminé");
        }
    }

    @Test
    void un_etat_inconnu_n_empeche_pas_la_dialyse() {
        assertTrue(ProgrammationSeance.evaluer(etat("ACTIF", null), JOUR).isEmpty());
    }
}
