package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.application.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * SE6, D9 (ADR 0093, à confirmer) : un échec de diffusion de {@code SEANCE_SUPPRIMEE} est journalisé et n'annule ni ne fait échouer la
 * suppression déjà validée (le legacy répondait une 500 APRÈS avoir effacé la séance).
 */
class DiffusionsFournieesLegacyTest {

    private static final UUID CENTRE = new UUID(1, 1);
    private static final UUID SEANCE = new UUID(2, 2);
    private static final UUID PATIENT = new UUID(3, 3);

    @Test
    void la_suppression_d_une_seance_est_diffusee_avec_le_jour_lu() {
        NotificationService notifications = mock(NotificationService.class);

        new DiffusionsFournieesLegacy(notifications).seanceSupprimee(CENTRE, SEANCE, PATIENT, "2026-01-01");

        verify(notifications).notifySeanceSupprimee(CENTRE, SEANCE, PATIENT, LocalDate.of(2026, 1, 1));
    }

    @Test
    void un_echec_de_la_diffusion_de_la_suppression_ne_remonte_pas_a_l_appelant() {
        NotificationService notifications = mock(NotificationService.class);
        doThrow(new IllegalStateException("courtier indisponible")).when(notifications)
                .notifySeanceSupprimee(any(), any(), any(), any());

        assertDoesNotThrow(() -> new DiffusionsFournieesLegacy(notifications).seanceSupprimee(CENTRE, SEANCE, PATIENT, "2026-01-01"));
    }
}
