package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Port sortant : le journal des mouvements (table {@code mouvement_patient}), en ajout seulement. */
public interface MouvementsDesPatients {

    void ajouter(MouvementDuPatient mouvement);
}
