package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port de persistance des préférences de planification des patients (toujours borné au centre — AGENTS.md §2).
 */
public interface PreferencePatientPort {

    /**
     * Préférences enregistrées des patients du centre (lecture de l'optimisation).
     */
    Map<UUID, PreferencePatient> preferences(UUID centerId);

    /**
     * Patients actifs du centre (placés ou en attente) avec leurs préférences, par nom (AGENTS.md §9).
     */
    PagedResult<Ligne> lister(UUID centerId, int page, int size);

    /**
     * @return faux si le patient n'appartient pas au centre
     */
    boolean enregistrer(UUID centerId, PreferencePatient preference);

    /**
     * Patient vu depuis l'écran des préférences : jours prescrits et créneau actuel, avec sa préférence (jamais nulle).
     */
    record Ligne(UUID patientId, String nom, Set<JourSemaine> jours, UUID creneauActuelId,
                 PreferencePatient preference) {
    }
}
