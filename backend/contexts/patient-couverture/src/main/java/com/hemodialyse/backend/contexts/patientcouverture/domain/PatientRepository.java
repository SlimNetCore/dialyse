package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/**
 * Port sortant : les patients (table {@code patients}). Une lecture ne rend que les patients du centre demandé : un patient d'un
 * autre centre est introuvable.
 */
public interface PatientRepository {

    Optional<Patient> trouver(CentreId centre, PatientId id);

    /** Patient du centre qui porte ce numéro d'assurance (numéro normalisé), s'il y en a un. */
    Optional<PatientId> trouverLeDetenteurDuNumero(CentreId centre, NumeroAssurance numero);

    /** Crée ou met à jour le patient (fiche) ; la place est écrite par la Planification, jamais par cet appel seul. */
    void enregistrer(Patient patient);
}
