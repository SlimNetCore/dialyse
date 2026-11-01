package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

public interface MouvementPatientRepository {
    void sauvegarder(MouvementPatient mouvement);

    List<MouvementPatient> listerParPatient(PatientId patientId, CentreId centreId);
}