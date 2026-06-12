package com.hemodialyse.backend.domain.seance.model;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public class MedicamentSeance {
    private UUID id;
    private UUID voletParamedicalId;
    private UUID centerId;
    private String nomMedicament;
    private String dose;
    private String voie;
    private LocalTime heureInjection;
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getVoletParamedicalId() {
        return voletParamedicalId;
    }

    public void setVoletParamedicalId(UUID v) {
        this.voletParamedicalId = v;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getNomMedicament() {
        return nomMedicament;
    }

    public void setNomMedicament(String nomMedicament) {
        this.nomMedicament = nomMedicament;
    }

    public String getDose() {
        return dose;
    }

    public void setDose(String dose) {
        this.dose = dose;
    }

    public String getVoie() {
        return voie;
    }

    public void setVoie(String voie) {
        this.voie = voie;
    }

    public LocalTime getHeureInjection() {
        return heureInjection;
    }

    public void setHeureInjection(LocalTime heureInjection) {
        this.heureInjection = heureInjection;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

