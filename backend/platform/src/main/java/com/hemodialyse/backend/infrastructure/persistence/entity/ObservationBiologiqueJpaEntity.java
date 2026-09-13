package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "observations_biologiques")
public class ObservationBiologiqueJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "demande_examen_id")
    private UUID demandeExamenId;

    @Column(name = "analyte_code_system", nullable = false, length = 10)
    private String analyteCodeSystem;

    @Column(name = "analyte_code", nullable = false, length = 20)
    private String analyteCode;

    @Column(name = "analyte_code_display", length = 255)
    private String analyteCodeDisplay;

    @Column(name = "valeur_num", precision = 14, scale = 4)
    private BigDecimal valeurNum;

    @Column(name = "unite", length = 20)
    private String unite;

    @Column(name = "valeur_texte", length = 500)
    private String valeurTexte;

    @Column(name = "date_prelevement", nullable = false)
    private LocalDate datePrelevement;

    @Column(name = "statut", nullable = false, length = 15)
    private String statut;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
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

    public UUID getDemandeExamenId() {
        return demandeExamenId;
    }

    public void setDemandeExamenId(UUID demandeExamenId) {
        this.demandeExamenId = demandeExamenId;
    }

    public String getAnalyteCodeSystem() {
        return analyteCodeSystem;
    }

    public void setAnalyteCodeSystem(String analyteCodeSystem) {
        this.analyteCodeSystem = analyteCodeSystem;
    }

    public String getAnalyteCode() {
        return analyteCode;
    }

    public void setAnalyteCode(String analyteCode) {
        this.analyteCode = analyteCode;
    }

    public String getAnalyteCodeDisplay() {
        return analyteCodeDisplay;
    }

    public void setAnalyteCodeDisplay(String analyteCodeDisplay) {
        this.analyteCodeDisplay = analyteCodeDisplay;
    }

    public BigDecimal getValeurNum() {
        return valeurNum;
    }

    public void setValeurNum(BigDecimal valeurNum) {
        this.valeurNum = valeurNum;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public String getValeurTexte() {
        return valeurTexte;
    }

    public void setValeurTexte(String valeurTexte) {
        this.valeurTexte = valeurTexte;
    }

    public LocalDate getDatePrelevement() {
        return datePrelevement;
    }

    public void setDatePrelevement(LocalDate datePrelevement) {
        this.datePrelevement = datePrelevement;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
