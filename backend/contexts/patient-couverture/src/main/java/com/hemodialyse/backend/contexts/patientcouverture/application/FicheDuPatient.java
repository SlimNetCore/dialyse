package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;

/**
 * Lecture d'un patient (DTO de lecture : l'interface la traduit en JSON, jamais l'agrégat).
 *
 * @param generateur description du générateur affecté, nulle s'il n'y en a pas
 * @param etatBrut   état tel qu'enregistré (la valeur brute d'un état inconnu est rendue telle quelle, contrat inchangé)
 */
public record FicheDuPatient(PatientId id, CentreId centre, String code, boolean vacancier, Instant creeLe, Fiche fiche,
                             GenerateursDuCentre.Generateur generateur, String etatBrut) {
}
