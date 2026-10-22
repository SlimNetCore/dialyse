package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.util.Collection;
import java.util.Map;

/**
 * Port sortant : nom d'affichage des patients (fait lu chez Patient et couverture, ACL SQL tant que ce n'est pas un contexte).
 */
public interface NomsDesPatients {

    /**
     * Nom d'affichage (« prénom nom ») de chaque patient connu de ce centre ; un patient inconnu du centre ou d'un autre
     * centre est absent du résultat.
     */
    Map<PatientId, String> parPatient(CentreId centre, Collection<PatientId> patients);
}
