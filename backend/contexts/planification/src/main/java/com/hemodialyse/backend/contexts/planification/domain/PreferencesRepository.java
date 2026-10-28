package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

import java.util.Map;

/**
 * Port sortant : les préférences de planification des patients d'un centre.
 */
public interface PreferencesRepository {

    /** Les préférences enregistrées du centre (lecture de l'optimisation). */
    Map<PatientId, PreferenceDePlanification> duCentre(CentreId centre);

    /** Remplace la préférence du patient (crée la ligne si besoin). */
    void enregistrer(CentreId centre, PreferenceDePlanification preference);
}
