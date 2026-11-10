package com.hemodialyse.backend.contexts.pilotagedirection.interfaces;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HorlogeDuPilotage;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.planifie.CaptureDuMoisEcoulePlanifiee;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

/** Le câblage du cron n'est pas couvert par le filet (la méthode appelée l'est) : on lit ici l'annotation, comme le legacy la déclarait. */
class CaptureDuMoisEcoulePlanifieeTest {

    @Test
    void la_capture_est_planifiee_le_premier_du_mois_a_deux_heures_trente() throws NoSuchMethodException {
        Scheduled planification = CaptureDuMoisEcoulePlanifiee.class.getMethod("figerLeMoisEcoule").getAnnotation(Scheduled.class);

        assertThat(planification).isNotNull();
        assertThat(planification.cron()).isEqualTo("0 30 2 1 * *");
    }

    /** KB-DIR-03 (lot L6) : le cron suit le fuseau de référence, comme les autres tâches de nuit du dépôt (propriété, défaut Africa/Algiers). */
    @Test
    void la_capture_est_planifiee_au_fuseau_de_reference_et_non_a_celui_de_la_jvm() throws NoSuchMethodException {
        Scheduled planification = CaptureDuMoisEcoulePlanifiee.class.getMethod("figerLeMoisEcoule").getAnnotation(Scheduled.class);

        assertThat(planification.zone()).isEqualTo(HorlogeDuPilotage.PROPRIETE_DU_FUSEAU);
        assertThat(planification.zone()).isEqualTo("${app.pilotage.fuseau-par-defaut:${app.absences.fuseau-par-defaut:Africa/Algiers}}");
    }
}
