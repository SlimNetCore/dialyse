package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementPatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

public class MouvementsJdbc implements MouvementPatientRepository {
    @Override
    public void sauvegarder(MouvementPatient mouvement) {
        // pas de table dediee en v1 ; mouvements portes par l'etat patient
    }

    @Override
    public List<MouvementPatient> listerParPatient(com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId patientId, CentreId centreId) {
        return List.of();
    }
}