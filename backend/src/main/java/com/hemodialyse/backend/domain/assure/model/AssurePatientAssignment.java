package com.hemodialyse.backend.domain.assure.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AssurePatientAssignment {
    private UUID patientId;
    private String numeroAssurance;
    private UUID centerId;
    private boolean primary;
    private OffsetDateTime dateAffectation;

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID v) { this.patientId = v; }
    public String getNumeroAssurance() { return numeroAssurance; }
    public void setNumeroAssurance(String v) { this.numeroAssurance = v; }
    public UUID getCenterId() { return centerId; }
    public void setCenterId(UUID v) { this.centerId = v; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean v) { this.primary = v; }
    public OffsetDateTime getDateAffectation() { return dateAffectation; }
    public void setDateAffectation(OffsetDateTime v) { this.dateAffectation = v; }
}

