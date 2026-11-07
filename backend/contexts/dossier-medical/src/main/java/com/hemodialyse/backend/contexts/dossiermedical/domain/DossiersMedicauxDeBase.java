package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/**
 * Port sortant du domaine : les dossiers médicaux de base (table {@code dossier_medical_patient}, inchangée ; unicité globale de
 * {@code patient_id}). Ordre global des verrous : centre (Planification), fiches, {@code app_user}, puis verrous consultatifs.
 */
public interface DossiersMedicauxDeBase {
    /** Lit le dossier du patient dans ce centre, sans verrou. */
    Optional<DossierMedicalDeBase> duPatient(CentreId centre, PatientId patient);

    /**
     * Verrou d'ouverture du dossier du patient (un seul enregistrement concurrent), tenu jusqu'à la fin de la transaction ; lève
     * {@link EcritureConcurrenteSurUnDossierMedical} si le délai est dépassé.
     */
    void verrouillerLEnregistrement(PatientId patient);

    /** Lit le dossier avec verrou de ligne. Lève {@link EcritureConcurrenteSurUnDossierMedical}. */
    Optional<DossierMedicalDeBase> duPatientEnVerrouillant(CentreId centre, PatientId patient);

    /** Insère un dossier. Un autre dossier existe déjà pour ce patient (unicité globale) : {@link EcritureConcurrenteSurUnDossierMedical}. */
    void ajouter(DossierMedicalDeBase dossier);

    void enregistrer(DossierMedicalDeBase dossier);
}
