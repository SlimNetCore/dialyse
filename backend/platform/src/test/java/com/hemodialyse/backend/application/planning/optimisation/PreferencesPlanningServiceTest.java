package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.PreferencePatientPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.ProfilInfirmierPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreferencesPlanningServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private final PreferencePatientPort preferences = mock(PreferencePatientPort.class);
    private final ProfilInfirmierPort profils = mock(ProfilInfirmierPort.class);
    private final ReglagesOptimisationPort reglages = mock(ReglagesOptimisationPort.class);
    private final PlanningDonneesPort planning = mock(PlanningDonneesPort.class);
    private final PreferencesPlanningService service = new PreferencesPlanningService(preferences, profils, reglages,
            planning);

    @Test
    void should_refuse_a_preferred_slot_of_another_center_without_saving() {
        UUID creneauEtranger = UUID.randomUUID();
        when(planning.creneauxDuCentre(CENTRE)).thenReturn(Set.of(UUID.randomUUID()));

        BusinessException e = catchThrowableOfType(BusinessException.class, () -> service.enregistrerPreference(CENTRE,
                new PreferencePatient(UUID.randomUUID(), creneauEtranger, null, false)));

        assertThat(e.getCode()).isEqualTo("PREFERENCE_CRENEAU_INCONNU");
        verify(preferences, never()).enregistrer(any(), any());
    }

    @Test
    void should_refuse_a_patient_unknown_to_the_center() {
        when(preferences.enregistrer(any(), any())).thenReturn(false);

        BusinessException e = catchThrowableOfType(BusinessException.class, () -> service.enregistrerPreference(CENTRE,
                PreferencePatient.aucune(UUID.randomUUID())));

        assertThat(e.getCode()).isEqualTo("PREFERENCE_PATIENT_INTROUVABLE");
    }

    @Test
    void should_save_a_profile_or_refuse_an_unknown_nurse() {
        ProfilInfirmier profil = ProfilInfirmier.parDefaut(UUID.randomUUID());
        when(profils.enregistrer(CENTRE, profil)).thenReturn(true);
        assertThat(service.enregistrerProfil(CENTRE, profil)).isEqualTo(profil);

        when(profils.enregistrer(CENTRE, profil)).thenReturn(false);
        assertThat(catchThrowableOfType(BusinessException.class, () -> service.enregistrerProfil(CENTRE, profil))
                .getCode()).isEqualTo("PROFIL_INFIRMIER_INTROUVABLE");
    }

    @Test
    void should_save_the_settings_of_the_center() {
        ReglagesOptimisation nouveaux = new ReglagesOptimisation(true, 4, 35, 2);

        assertThat(service.enregistrerReglages(CENTRE, nouveaux)).isEqualTo(nouveaux);
        verify(reglages).enregistrer(CENTRE, nouveaux);
    }
}
