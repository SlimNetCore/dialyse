package com.hemodialyse.backend.domain.patient.model;

/**
 * Mouvement enrichi pour l'affichage : identité du patient et libellés de l'affectation occupée.
 */
public record MouvementLigne(MouvementPatient mouvement, String patientNom, String patientCode, String salleNom,
                             String creneauLibelle, String generateurCode) {
}
