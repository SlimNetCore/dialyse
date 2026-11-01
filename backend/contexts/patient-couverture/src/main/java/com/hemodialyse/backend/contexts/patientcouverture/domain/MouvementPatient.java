package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MouvementPatient(MouvementPatientId id, PatientId patientId, CentreId centreId, MouvementType type,
                               LocalDate dateEvenement, String motif, UUID auteurId, Instant creeLe) {
    public MouvementPatient {
        if (id == null) throw new IllegalArgumentException("Mouvement requis");
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (type == null) throw new IllegalArgumentException("Type requis");
        if (dateEvenement == null) throw new IllegalArgumentException("Date evenement requise");
        if (creeLe == null) throw new IllegalArgumentException("Instant requis");
    }
}