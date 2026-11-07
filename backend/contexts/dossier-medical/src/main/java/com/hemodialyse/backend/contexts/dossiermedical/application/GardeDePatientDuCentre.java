package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.GardeDePatient;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Implémente le langage publié {@link GardeDePatient} avec le port {@link PatientsDuCentre} : 404 pour un patient inconnu du centre. */
public class GardeDePatientDuCentre implements GardeDePatient {
    private final PatientsDuCentre patients;

    public GardeDePatientDuCentre(PatientsDuCentre patients) {
        this.patients = patients;
    }

    @Override
    public void exiger(UUID centre, UUID patient) {
        if (!patients.existe(new CentreId(centre), new PatientId(patient))) throw new PatientIntrouvable();
    }
}
