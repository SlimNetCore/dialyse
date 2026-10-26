package com.hemodialyse.backend.contexts.stock.interfaces.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Planification (RG-STK-061) : tous les jours à 07:00, au fuseau configuré (celui des Absences par défaut, Africa/Algiers).
 */
class ControleDesPeremptionsPlanifieTest {

    @Test
    void le_controle_est_planifie_chaque_jour_a_sept_heures_au_fuseau_par_defaut_du_serveur() throws Exception {
        Scheduled planification = ControleDesPeremptionsPlanifie.class.getMethod("controlerLesPeremptions")
                .getAnnotation(Scheduled.class);
        assertEquals("0 0 7 * * *", planification.cron());
        assertEquals("${app.stock.fuseau-par-defaut:${app.absences.fuseau-par-defaut:Africa/Algiers}}", planification.zone());
    }
}
