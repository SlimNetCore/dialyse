package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeEquipement;
import com.hemodialyse.backend.domain.planning.port.GenerateurImpactPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Un générateur de dialyse en service qui devient indisponible alerte l'administration avec ses patients ; rien dans les
 * autres cas.
 */
class GenerateurIndisponibleServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();

    private final GenerateurImpactPort impact = mock(GenerateurImpactPort.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final GenerateurIndisponibleService service = new GenerateurIndisponibleService(impact, notifications);

    private Equipement equipement(TypeEquipement type) {
        return Equipement.creer("A-G1", "Générateur", type, null, null, null,
                java.time.OffsetDateTime.parse("2024-01-15T00:00:00Z"), CENTRE, null, UUID.randomUUID(), null, null);
    }

    @Test
    void should_alert_with_the_patients_placed_on_a_dialysis_generator_that_goes_out_of_service() {
        Equipement generateur = equipement(TypeEquipement.GENERATEUR_DIALYSE);
        generateur.marquerHorsService("Panne", UUID.randomUUID());
        when(impact.patientsPlacesSur(eq(CENTRE), eq(generateur.getId()), any())).thenReturn(List.of("BENALI Karim"));

        service.signaler(generateur, StatutEquipement.EN_SERVICE);

        verify(notifications).notifyGenerateurIndisponible(CENTRE, "A-G1", "HORS_SERVICE", List.of("BENALI Karim"));
    }

    @Test
    void should_ignore_other_equipment_and_changes_that_do_not_make_a_working_generator_unavailable() {
        Equipement autre = equipement(TypeEquipement.GENERATEUR_DIALYSE);
        Equipement osmoseur = equipement(TypeEquipement.RO_REVERSE_OSMOSIS);
        osmoseur.marquerHorsService("Panne", UUID.randomUUID());

        service.signaler(osmoseur, StatutEquipement.EN_SERVICE);
        service.signaler(autre, StatutEquipement.EN_SERVICE);
        service.signaler(autre, null);

        verifyNoInteractions(notifications);
    }

    @Test
    void should_not_alert_again_for_a_generator_that_was_already_unavailable() {
        Equipement generateur = equipement(TypeEquipement.GENERATEUR_DIALYSE);
        generateur.marquerHorsService("Panne", UUID.randomUUID());

        service.signaler(generateur, StatutEquipement.EN_MAINTENANCE);

        verify(notifications, never()).notifyGenerateurIndisponible(any(), any(), any(), any());
    }

    @Test
    void should_never_fail_the_status_change_when_the_alert_cannot_be_built() {
        Equipement generateur = equipement(TypeEquipement.GENERATEUR_DIALYSE);
        generateur.marquerHorsService("Panne", UUID.randomUUID());
        doThrow(new IllegalStateException("base inaccessible")).when(impact).patientsPlacesSur(any(), any(), any());

        service.signaler(generateur, StatutEquipement.EN_SERVICE);

        verify(notifications, never()).notifyGenerateurIndisponible(any(), any(), any(), any());
    }
}
