package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bons_sortie", indexes = {
        @Index(name = "idx_bs_center", columnList = "center_id"),
        @Index(name = "idx_bs_seance", columnList = "seance_id"),
        @Index(name = "idx_bs_reference", columnList = "center_id, reference")
})
public class BonSortieJpaEntity {
    @Id
    private UUID id;
    @Column(name = "center_id", nullable = false)
    private UUID centerId;
    @Column(name = "reference", nullable = false)
    private String reference;
    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;
    @Column(name = "patient_id")
    private UUID patientId;
    @Column(name = "poste")
    private String poste;
    @Column(name = "date_sortie")
    private LocalDate dateSortie;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public UUID getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(UUID seanceId) {
        this.seanceId = seanceId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public String getPoste() {
        return poste;
    }

    public void setPoste(String poste) {
        this.poste = poste;
    }

    public LocalDate getDateSortie() {
        return dateSortie;
    }

    public void setDateSortie(LocalDate dateSortie) {
        this.dateSortie = dateSortie;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

