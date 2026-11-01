package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssurePatientRepository {
    Optional<AssurePatientAssignment> trouverPrincipal(PatientId patientId, CentreId centreId);

    List<AssurePatientAssignment> listerHistorique(PatientId patientId, CentreId centreId);

    void terminerActifs(PatientId patientId, CentreId centreId, LocalDate dateFin);

    void sauvegarder(AssurePatientAssignment a);

    Optional<AssurePatientAssignment> trouverParId(UUID id);
}