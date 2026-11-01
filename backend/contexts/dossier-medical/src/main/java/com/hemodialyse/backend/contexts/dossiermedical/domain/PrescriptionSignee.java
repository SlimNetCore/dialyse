package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionSignee(UUID prescriptionId, UUID patientId, CentreId centreId, Instant dateSignature) {
}