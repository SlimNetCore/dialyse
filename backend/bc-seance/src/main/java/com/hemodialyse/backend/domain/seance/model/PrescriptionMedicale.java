package com.hemodialyse.backend.domain.seance.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PrescriptionMedicale {
    private UUID id;
    private UUID patientId;
    private UUID centerId;
    private LocalDate datePrescription;
    private UUID medecinId;
    private Integer qbCible;
    private Integer qdCible;
    private Integer ufMaxMl;
    private Integer dureeCibleMin;
    private String typeDialyseurPrescrit;
    private String anticoagTypePrescrit;
    private String epoMolecule;
    private Integer epoDoseUi;
    private String epoVoie;
    private String epoFrequence;
    private String ferMolecule;
    private Integer ferDoseMg;
    private String ferVoie;
    private String ferFrequence;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

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

    public LocalDate getDatePrescription() {
        return datePrescription;
    }

    public void setDatePrescription(LocalDate datePrescription) {
        this.datePrescription = datePrescription;
    }

    public UUID getMedecinId() {
        return medecinId;
    }

    public void setMedecinId(UUID medecinId) {
        this.medecinId = medecinId;
    }

    public Integer getQbCible() {
        return qbCible;
    }

    public void setQbCible(Integer qbCible) {
        this.qbCible = qbCible;
    }

    public Integer getQdCible() {
        return qdCible;
    }

    public void setQdCible(Integer qdCible) {
        this.qdCible = qdCible;
    }

    public Integer getUfMaxMl() {
        return ufMaxMl;
    }

    public void setUfMaxMl(Integer ufMaxMl) {
        this.ufMaxMl = ufMaxMl;
    }

    public Integer getDureeCibleMin() {
        return dureeCibleMin;
    }

    public void setDureeCibleMin(Integer dureeCibleMin) {
        this.dureeCibleMin = dureeCibleMin;
    }

    public String getTypeDialyseurPrescrit() {
        return typeDialyseurPrescrit;
    }

    public void setTypeDialyseurPrescrit(String typeDialyseurPrescrit) {
        this.typeDialyseurPrescrit = typeDialyseurPrescrit;
    }

    public String getAnticoagTypePrescrit() {
        return anticoagTypePrescrit;
    }

    public void setAnticoagTypePrescrit(String anticoagTypePrescrit) {
        this.anticoagTypePrescrit = anticoagTypePrescrit;
    }

    public String getEpoMolecule() {
        return epoMolecule;
    }

    public void setEpoMolecule(String epoMolecule) {
        this.epoMolecule = epoMolecule;
    }

    public Integer getEpoDoseUi() {
        return epoDoseUi;
    }

    public void setEpoDoseUi(Integer epoDoseUi) {
        this.epoDoseUi = epoDoseUi;
    }

    public String getEpoVoie() {
        return epoVoie;
    }

    public void setEpoVoie(String epoVoie) {
        this.epoVoie = epoVoie;
    }

    public String getEpoFrequence() {
        return epoFrequence;
    }

    public void setEpoFrequence(String epoFrequence) {
        this.epoFrequence = epoFrequence;
    }

    public String getFerMolecule() {
        return ferMolecule;
    }

    public void setFerMolecule(String ferMolecule) {
        this.ferMolecule = ferMolecule;
    }

    public Integer getFerDoseMg() {
        return ferDoseMg;
    }

    public void setFerDoseMg(Integer ferDoseMg) {
        this.ferDoseMg = ferDoseMg;
    }

    public String getFerVoie() {
        return ferVoie;
    }

    public void setFerVoie(String ferVoie) {
        this.ferVoie = ferVoie;
    }

    public String getFerFrequence() {
        return ferFrequence;
    }

    public void setFerFrequence(String ferFrequence) {
        this.ferFrequence = ferFrequence;
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

