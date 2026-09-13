package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "antecedents_medicaux")
public class AntecedentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "type_antecedent", nullable = false, length = 20)
    private String typeAntecedent;

    @Column(name = "diagnostic_code_system", length = 10)
    private String diagnosticCodeSystem;

    @Column(name = "diagnostic_code", length = 20)
    private String diagnosticCode;

    @Column(name = "diagnostic_code_display", length = 255)
    private String diagnosticCodeDisplay;

    @Column(name = "libelle_libre", length = 255)
    private String libelleLibre;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "statut_clinique", nullable = false, length = 20)
    private String statutClinique;

    @Column(name = "severite", length = 50)
    private String severite;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

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

    public String getTypeAntecedent() {
        return typeAntecedent;
    }

    public void setTypeAntecedent(String typeAntecedent) {
        this.typeAntecedent = typeAntecedent;
    }

    public String getDiagnosticCodeSystem() {
        return diagnosticCodeSystem;
    }

    public void setDiagnosticCodeSystem(String diagnosticCodeSystem) {
        this.diagnosticCodeSystem = diagnosticCodeSystem;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }

    public void setDiagnosticCode(String diagnosticCode) {
        this.diagnosticCode = diagnosticCode;
    }

    public String getDiagnosticCodeDisplay() {
        return diagnosticCodeDisplay;
    }

    public void setDiagnosticCodeDisplay(String diagnosticCodeDisplay) {
        this.diagnosticCodeDisplay = diagnosticCodeDisplay;
    }

    public String getLibelleLibre() {
        return libelleLibre;
    }

    public void setLibelleLibre(String libelleLibre) {
        this.libelleLibre = libelleLibre;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public String getStatutClinique() {
        return statutClinique;
    }

    public void setStatutClinique(String statutClinique) {
        this.statutClinique = statutClinique;
    }

    public String getSeverite() {
        return severite;
    }

    public void setSeverite(String severite) {
        this.severite = severite;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
