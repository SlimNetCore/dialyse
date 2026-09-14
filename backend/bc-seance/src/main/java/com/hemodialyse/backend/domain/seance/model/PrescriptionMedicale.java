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
    private UUID epoArticleId;
    private Integer epoDoseUi;
    private String epoVoie;
    private Integer epoFrequenceValeur;
    private UniteFrequence epoFrequenceUnite;
    private UUID ferArticleId;
    private Integer ferDoseMg;
    private String ferVoie;
    private Integer ferFrequenceValeur;
    private UniteFrequence ferFrequenceUnite;
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

    public UUID getEpoArticleId() {
        return epoArticleId;
    }

    public void setEpoArticleId(UUID epoArticleId) {
        this.epoArticleId = epoArticleId;
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

    public Integer getEpoFrequenceValeur() {
        return epoFrequenceValeur;
    }

    public void setEpoFrequenceValeur(Integer epoFrequenceValeur) {
        this.epoFrequenceValeur = epoFrequenceValeur;
    }

    public UniteFrequence getEpoFrequenceUnite() {
        return epoFrequenceUnite;
    }

    public void setEpoFrequenceUnite(UniteFrequence epoFrequenceUnite) {
        this.epoFrequenceUnite = epoFrequenceUnite;
    }

    public UUID getFerArticleId() {
        return ferArticleId;
    }

    public void setFerArticleId(UUID ferArticleId) {
        this.ferArticleId = ferArticleId;
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

    public Integer getFerFrequenceValeur() {
        return ferFrequenceValeur;
    }

    public void setFerFrequenceValeur(Integer ferFrequenceValeur) {
        this.ferFrequenceValeur = ferFrequenceValeur;
    }

    public UniteFrequence getFerFrequenceUnite() {
        return ferFrequenceUnite;
    }

    public void setFerFrequenceUnite(UniteFrequence ferFrequenceUnite) {
        this.ferFrequenceUnite = ferFrequenceUnite;
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
