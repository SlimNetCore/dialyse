package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient le dossier médical de base du patient (délai de verrou de 5 s dépassé), ou un dossier existe déjà pour ce
 * patient dans un autre centre (unicité globale de {@code patient_id}) : 409 {@code DOSSIER_MEDICAL_ECRITURE_CONCURRENTE}
 * (KB-MED-98, avant : 500).
 */
public class EcritureConcurrenteSurUnDossierMedical extends EcritureConcurrente {
}
