package com.hemodialyse.backend.domain.insurance.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Domain entity — Attestation d'ouverture de droit (no JPA) */
public class AttestationDroit {
    private UUID id;
    private UUID patientId;
    private UUID centerId;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private OffsetDateTime createdAt;

    protected AttestationDroit() {}

    public AttestationDroit(UUID id, UUID patientId, UUID centerId, LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) throw new IllegalArgumentException("Dates attestation obligatoires");
        if (dateFin.isBefore(dateDebut)) throw new IllegalArgumentException("Date fin avant date debut");
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getCenterId() { return centerId; }
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setPatientId(UUID v) { this.patientId = v; }
    public void setCenterId(UUID v) { this.centerId = v; }
    public void setDateDebut(LocalDate v) { this.dateDebut = v; }
    public void setDateFin(LocalDate v) { this.dateFin = v; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}

