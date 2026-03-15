package com.hemodialyse.backend.domain.insurance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attestation_droit")
public class AttestationDroit {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AttestationDroit() {
        // JPA
    }

    public AttestationDroit(UUID id, UUID patientId, UUID centerId, LocalDate dateDebut, LocalDate dateFin) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }
}

