package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bons_reception", indexes = {
        @Index(name = "idx_br_center", columnList = "center_id"),
        @Index(name = "idx_br_reference", columnList = "center_id, reference")
})
public class BonReceptionJpaEntity {
    @Id
    private UUID id;
    @Column(name = "center_id", nullable = false)
    private UUID centerId;
    @Column(name = "reference", nullable = false)
    private String reference;
    @Column(name = "bon_commande_id")
    private UUID bonCommandeId;
    @Column(name = "fournisseur_id")
    private UUID fournisseurId;
    @Column(name = "date_reception")
    private LocalDate dateReception;
    @Column(name = "statut", nullable = false)
    private String statut;
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

    public UUID getBonCommandeId() {
        return bonCommandeId;
    }

    public void setBonCommandeId(UUID bonCommandeId) {
        this.bonCommandeId = bonCommandeId;
    }

    public UUID getFournisseurId() {
        return fournisseurId;
    }

    public void setFournisseurId(UUID fournisseurId) {
        this.fournisseurId = fournisseurId;
    }

    public LocalDate getDateReception() {
        return dateReception;
    }

    public void setDateReception(LocalDate dateReception) {
        this.dateReception = dateReception;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
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

