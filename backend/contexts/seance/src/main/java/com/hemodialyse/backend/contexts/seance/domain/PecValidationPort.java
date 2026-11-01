package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

public interface PecValidationPort {
    boolean estValide(PecId pecId, CentreId centreId, PatientId patientId);
}