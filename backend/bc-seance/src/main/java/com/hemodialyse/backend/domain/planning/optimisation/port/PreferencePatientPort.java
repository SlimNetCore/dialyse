package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;

import java.util.Map;
import java.util.UUID;

/**
 * Lecture des préférences de planification des patients, pour l'optimisation. Les préférences sont tenues par le contexte
 * Planification (slice P1) : ce port en est la lecture, implémentée par une ACL sur son langage publié.
 */
public interface PreferencePatientPort {

    /**
     * Préférences enregistrées des patients du centre (lecture de l'optimisation).
     */
    Map<UUID, PreferencePatient> preferences(UUID centerId);
}
