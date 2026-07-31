package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "volet_medical")
public class VoletMedicalJpaEntity {
    @Id
    private UUID id;

    @Column(name = "seance_id", nullable = false, unique = true)
    private UUID seanceId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "prescription", columnDefinition = "TEXT")
    private String prescription;

    @Column(name = "tolerance_seance", columnDefinition = "TEXT")
    private String toleranceSeance;

    @Column(name = "examen_clinique", columnDefinition = "TEXT")
    private String examenClinique;

    @Column(name = "resultats_biologiques", columnDefinition = "TEXT")
    private String resultatsBiologiques;

    @Column(name = "ajustements_therapeutiques", columnDefinition = "TEXT")
    private String ajustementsTherapeutiques;

    @Column(name = "conclusion_medicale", columnDefinition = "TEXT")
    private String conclusionMedicale;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(UUID seanceId) {
        this.seanceId = seanceId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getPrescription() {
        return prescription;
    }

    public void setPrescription(String prescription) {
        this.prescription = prescription;
    }

    public String getToleranceSeance() {
        return toleranceSeance;
    }

    public void setToleranceSeance(String toleranceSeance) {
        this.toleranceSeance = toleranceSeance;
    }

    public String getExamenClinique() {
        return examenClinique;
    }

    public void setExamenClinique(String examenClinique) {
        this.examenClinique = examenClinique;
    }

    public String getResultatsBiologiques() {
        return resultatsBiologiques;
    }

    public void setResultatsBiologiques(String resultatsBiologiques) {
        this.resultatsBiologiques = resultatsBiologiques;
    }

    public String getAjustementsTherapeutiques() {
        return ajustementsTherapeutiques;
    }

    public void setAjustementsTherapeutiques(String ajustementsTherapeutiques) {
        this.ajustementsTherapeutiques = ajustementsTherapeutiques;
    }

    public String getConclusionMedicale() {
        return conclusionMedicale;
    }

    public void setConclusionMedicale(String conclusionMedicale) {
        this.conclusionMedicale = conclusionMedicale;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

