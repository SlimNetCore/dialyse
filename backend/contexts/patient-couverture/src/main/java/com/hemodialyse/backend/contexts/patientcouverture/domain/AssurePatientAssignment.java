package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

public class AssurePatientAssignment {
    private final UUID id;
    private final PatientId patientId;
    private final CentreId centreId;
    private final AssureId numeroAssurance;
    private final boolean primary;
    private final LocalDate dateDebutAffectation;
    private LocalDate dateFinAffectation;

    public AssurePatientAssignment(UUID id, PatientId patientId, CentreId centreId, AssureId numeroAssurance,
                                   boolean primary, LocalDate dateDebutAffectation, LocalDate dateFinAffectation) {
        if (id == null) throw new IllegalArgumentException("Id requis");
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (numeroAssurance == null) throw new IllegalArgumentException("Assure requis");
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.numeroAssurance = numeroAssurance;
        this.primary = primary;
        this.dateDebutAffectation = dateDebutAffectation;
        this.dateFinAffectation = dateFinAffectation;
    }

    public boolean isActif() {
        return dateFinAffectation == null;
    }

    public void terminer(LocalDate dateFin) {
        this.dateFinAffectation = dateFin;
    }

    public UUID id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public AssureId numeroAssurance() {
        return numeroAssurance;
    }

    public boolean primary() {
        return primary;
    }

    public LocalDate dateDebutAffectation() {
        return dateDebutAffectation;
    }

    public LocalDate dateFinAffectation() {
        return dateFinAffectation;
    }
}