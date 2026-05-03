package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "attestation_droit")
public class AttestationJpaEntity {
    @Id private UUID id;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(name = "date_debut", nullable = false) private LocalDate dateDebut;
    @Column(name = "date_fin", nullable = false) private LocalDate dateFin;
    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getPatientId() { return patientId; } public void setPatientId(UUID v) { this.patientId = v; }
    public UUID getCenterId() { return centerId; } public void setCenterId(UUID v) { this.centerId = v; }
    public LocalDate getDateDebut() { return dateDebut; } public void setDateDebut(LocalDate v) { this.dateDebut = v; }
    public LocalDate getDateFin() { return dateFin; } public void setDateFin(LocalDate v) { this.dateFin = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}

