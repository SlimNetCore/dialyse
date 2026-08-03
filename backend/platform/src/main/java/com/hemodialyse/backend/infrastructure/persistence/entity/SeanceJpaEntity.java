package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seances", indexes = {
        @Index(name = "idx_seances_center_facture", columnList = "center_id, facture_id")
})
public class SeanceJpaEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "date_seance", nullable = false)
    private LocalDate dateSeance;

    @Column(name = "statut", nullable = false)
    private String statut;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "validated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime validatedAt;

    @Column(name = "signed_infirmier_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime signedInfirmierAt;

    @Column(name = "signed_infirmier_by", length = 100)
    private String signedInfirmierBy;

    @Column(name = "signed_medecin_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime signedMedecinAt;

    @Column(name = "signed_medecin_by", length = 100)
    private String signedMedecinBy;

    @Column(name = "facture_id")
    private UUID factureId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public LocalDate getDateSeance() {
        return dateSeance;
    }

    public void setDateSeance(LocalDate dateSeance) {
        this.dateSeance = dateSeance;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(OffsetDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public OffsetDateTime getSignedInfirmierAt() {
        return signedInfirmierAt;
    }

    public void setSignedInfirmierAt(OffsetDateTime signedInfirmierAt) {
        this.signedInfirmierAt = signedInfirmierAt;
    }

    public String getSignedInfirmierBy() {
        return signedInfirmierBy;
    }

    public void setSignedInfirmierBy(String signedInfirmierBy) {
        this.signedInfirmierBy = signedInfirmierBy;
    }

    public OffsetDateTime getSignedMedecinAt() {
        return signedMedecinAt;
    }

    public void setSignedMedecinAt(OffsetDateTime signedMedecinAt) {
        this.signedMedecinAt = signedMedecinAt;
    }

    public String getSignedMedecinBy() {
        return signedMedecinBy;
    }

    public void setSignedMedecinBy(String signedMedecinBy) {
        this.signedMedecinBy = signedMedecinBy;
    }

    public UUID getFactureId() {
        return factureId;
    }

    public void setFactureId(UUID factureId) {
        this.factureId = factureId;
    }
}
