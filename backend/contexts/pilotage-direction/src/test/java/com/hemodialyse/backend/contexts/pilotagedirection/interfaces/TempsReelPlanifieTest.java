package com.hemodialyse.backend.contexts.pilotagedirection.interfaces;

import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.planifie.TempsReelPlanifie;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Le câblage des cadences n'est pas couvert par le filet : on lit les annotations (mêmes délais que l'ancien {@code DirectionRealtimeScheduler}). */
class TempsReelPlanifieTest {

    @Test
    void les_signalements_sont_traites_chaque_seconde() throws Exception {
        Scheduled s = TempsReelPlanifie.class.getMethod("traiterLesSignalements").getAnnotation(Scheduled.class);
        assertEquals(1000, s.fixedDelay());
        assertEquals(-1, s.initialDelay());
    }

    @Test
    void le_balayage_de_securite_a_lieu_toutes_les_dix_secondes_apres_dix_secondes() throws Exception {
        Scheduled s = TempsReelPlanifie.class.getMethod("balayer").getAnnotation(Scheduled.class);
        assertEquals(10000, s.fixedDelay());
        assertEquals(10000, s.initialDelay());
    }
}
