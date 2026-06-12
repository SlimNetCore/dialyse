package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "volet_paramedical")
public class VoletParamedicalJpaEntity {
    @Id
    private UUID id;

    @Column(name = "seance_id", nullable = false, unique = true)
    private UUID seanceId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "poids_avant_kg")
    private BigDecimal poidsAvantKg;

    @Column(name = "poids_apres_kg")
    private BigDecimal poidsApresKg;

    @Column(name = "ta_avant", length = 30)
    private String taAvant;

    @Column(name = "ta_apres", length = 30)
    private String taApres;

    @Column(name = "duree_minutes")
    private Integer dureeMinutes;

    @Column(name = "debit_sang_ml_min")
    private Integer debitSangMlMin;

    @Column(name = "ultrafiltration_ml")
    private BigDecimal ultrafiltrationMl;

    @Column(name = "anticoagulant", length = 100)
    private String anticoagulant;

    @Column(name = "type_dialysat", length = 100)
    private String typeDialysat;

    @Column(name = "incidents", columnDefinition = "TEXT")
    private String incidents;

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

    public BigDecimal getPoidsAvantKg() {
        return poidsAvantKg;
    }

    public void setPoidsAvantKg(BigDecimal poidsAvantKg) {
        this.poidsAvantKg = poidsAvantKg;
    }

    public BigDecimal getPoidsApresKg() {
        return poidsApresKg;
    }

    public void setPoidsApresKg(BigDecimal poidsApresKg) {
        this.poidsApresKg = poidsApresKg;
    }

    public String getTaAvant() {
        return taAvant;
    }

    public void setTaAvant(String taAvant) {
        this.taAvant = taAvant;
    }

    public String getTaApres() {
        return taApres;
    }

    public void setTaApres(String taApres) {
        this.taApres = taApres;
    }

    public Integer getDureeMinutes() {
        return dureeMinutes;
    }

    public void setDureeMinutes(Integer dureeMinutes) {
        this.dureeMinutes = dureeMinutes;
    }

    public Integer getDebitSangMlMin() {
        return debitSangMlMin;
    }

    public void setDebitSangMlMin(Integer debitSangMlMin) {
        this.debitSangMlMin = debitSangMlMin;
    }

    public BigDecimal getUltrafiltrationMl() {
        return ultrafiltrationMl;
    }

    public void setUltrafiltrationMl(BigDecimal ultrafiltrationMl) {
        this.ultrafiltrationMl = ultrafiltrationMl;
    }

    public String getAnticoagulant() {
        return anticoagulant;
    }

    public void setAnticoagulant(String anticoagulant) {
        this.anticoagulant = anticoagulant;
    }

    public String getTypeDialysat() {
        return typeDialysat;
    }

    public void setTypeDialysat(String typeDialysat) {
        this.typeDialysat = typeDialysat;
    }

    public String getIncidents() {
        return incidents;
    }

    public void setIncidents(String incidents) {
        this.incidents = incidents;
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

