package com.hemodialyse.backend.domain.seance.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class VoletMedical {
    private UUID id;
    private UUID seanceId;
    private UUID centerId;
    private String prescription;
    private String toleranceSeance;
    private String examenClinique;
    private String resultatsBiologiques;
    private String ajustementsTherapeutiques;
    private String conclusionMedicale;
    private OffsetDateTime createdAt;
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

