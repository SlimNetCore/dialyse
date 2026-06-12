package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prescriptions_medicales")
public class PrescriptionMedicaleJpaEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "date_prescription", nullable = false)
    private LocalDate datePrescription;

    @Column(name = "medecin_id")
    private UUID medecinId;

    @Column(name = "qb_cible")
    private Integer qbCible;

    @Column(name = "qd_cible")
    private Integer qdCible;

    @Column(name = "uf_max_ml")
    private Integer ufMaxMl;

    @Column(name = "duree_cible_min")
    private Integer dureeCibleMin;

    @Column(name = "type_dialyseur_prescrit", length = 100)
    private String typeDialyseurPrescrit;

    @Column(name = "anticoag_type_prescrit", length = 20)
    private String anticoagTypePrescrit;

    @Column(name = "epo_molecule", length = 100)
    private String epoMolecule;

    @Column(name = "epo_dose_ui")
    private Integer epoDoseUi;

    @Column(name = "epo_voie", length = 10)
    private String epoVoie;

    @Column(name = "epo_frequence", length = 50)
    private String epoFrequence;

    @Column(name = "fer_molecule", length = 100)
    private String ferMolecule;

    @Column(name = "fer_dose_mg")
    private Integer ferDoseMg;

    @Column(name = "fer_voie", length = 10)
    private String ferVoie;

    @Column(name = "fer_frequence", length = 50)
    private String ferFrequence;

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

