package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ParametrageDeLaPlanification;
import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;
import com.hemodialyse.backend.domain.planning.optimisation.port.PreferencePatientPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACL du legacy vers les préférences des patients de la Planification (slice P1, ADR 0053) : lecture de l'optimisation par le
 * langage publié du contexte.
 */
@Component
public class PreferencesPatientsDuContexte implements PreferencePatientPort {

    private final ParametrageDeLaPlanification parametrage;

    public PreferencesPatientsDuContexte(ParametrageDeLaPlanification parametrage) {
        this.parametrage = parametrage;
    }

    @Override
    public Map<UUID, PreferencePatient> preferences(UUID centerId) {
        Map<UUID, PreferencePatient> preferences = new HashMap<>();
        parametrage.preferencesDesPatients(centerId).forEach((patient, p) -> preferences.put(patient,
                new PreferencePatient(patient, p.creneauPrefere(), p.seancesParSemaine(), p.joursAChoisir())));
        return preferences;
    }
}
