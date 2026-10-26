package com.hemodialyse.backend.contexts.stock.domain.peremptions;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RG-STK-061 : fenêtre J+30 (borne incluse), lot périmé signalé le lendemain de sa péremption puis chaque semaine.
 */
class RegleDesPeremptionsTest {

    private static final LocalDate JOUR = LocalDate.parse("2026-10-24");

    private static LocalDate dans(int jours) {
        return JOUR.plusDays(jours);
    }

    @Test
    void l_horizon_est_a_trente_jours() {
        assertEquals(dans(30), RegleDesPeremptions.horizon(JOUR));
    }

    @Test
    void un_lot_qui_perime_dans_les_trente_jours_est_signale_borne_incluse() {
        assertTrue(RegleDesPeremptions.aSignaler(dans(30), JOUR));
        assertTrue(RegleDesPeremptions.aSignaler(dans(29), JOUR));
        assertTrue(RegleDesPeremptions.aSignaler(dans(1), JOUR));
        assertTrue(RegleDesPeremptions.aSignaler(dans(0), JOUR), "le jour même, le lot n'est pas encore périmé");
    }

    @Test
    void un_lot_qui_perime_apres_trente_jours_n_est_pas_signale() {
        assertFalse(RegleDesPeremptions.aSignaler(dans(31), JOUR));
        assertFalse(RegleDesPeremptions.aSignaler(dans(90), JOUR));
    }

    @Test
    void un_lot_perime_est_signale_le_lendemain_puis_chaque_semaine() {
        assertTrue(RegleDesPeremptions.aSignaler(dans(-1), JOUR));
        assertTrue(RegleDesPeremptions.aSignaler(dans(-8), JOUR));
        assertTrue(RegleDesPeremptions.aSignaler(dans(-15), JOUR));
        assertTrue(RegleDesPeremptions.aSignaler(dans(-71), JOUR));
    }

    @Test
    void un_lot_perime_n_est_pas_signale_les_autres_jours_de_la_semaine() {
        for (int ecoule : new int[]{2, 3, 4, 5, 6, 7, 9, 14, 16}) {
            assertFalse(RegleDesPeremptions.aSignaler(dans(-ecoule), JOUR), "périmé depuis " + ecoule + " jours");
        }
    }

    @Test
    void un_lot_est_perime_a_partir_du_lendemain_de_sa_date() {
        assertFalse(RegleDesPeremptions.estPerime(dans(0), JOUR));
        assertFalse(RegleDesPeremptions.estPerime(dans(5), JOUR));
        assertTrue(RegleDesPeremptions.estPerime(dans(-1), JOUR));
    }

    @Test
    void un_lot_recu_deja_perime_est_signale_au_premier_controle_apres_sa_reception_et_une_seule_fois() {
        LocalDate perime = dans(-3);
        assertTrue(RegleDesPeremptions.aSignaler(perime, JOUR, dans(-1)), "créé hier : premier contrôle qui suit la réception");
        assertFalse(RegleDesPeremptions.aSignaler(perime, JOUR, dans(-2)), "créé avant-hier : déjà signalé hier");
        assertFalse(RegleDesPeremptions.aSignaler(perime, JOUR, JOUR), "créé aujourd'hui : signalé demain");
        assertFalse(RegleDesPeremptions.aSignaler(perime, JOUR, null), "création inconnue : règle hebdomadaire seule");
    }

    @Test
    void la_reception_recente_ne_change_rien_pour_un_lot_non_perime_ou_hors_fenetre() {
        assertFalse(RegleDesPeremptions.aSignaler(dans(31), JOUR, dans(-1)));
        assertTrue(RegleDesPeremptions.aSignaler(dans(5), JOUR, dans(-100)));
        assertTrue(RegleDesPeremptions.aSignaler(dans(-1), JOUR, dans(-100)), "la règle hebdomadaire s'applique toujours");
    }
}
