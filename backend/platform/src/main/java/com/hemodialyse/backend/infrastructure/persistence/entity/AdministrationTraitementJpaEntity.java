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
@Table(name = "administrations_anemie")
public class AdministrationTraitementJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "prescription_medicale_id")
    private UUID prescriptionMedicaleId;

    @Column(name = "type_traitement", nullable = false, length = 20)
    private String typeTraitement;

    @Column(name = "molecule", length = 100)
    private String molecule;

    @Column(name = "dose", precision = 10, scale = 3)
    private BigDecimal dose;

    @Column(name = "unite_dose", length = 20)
    private String uniteDose;

    @Column(name = "voie", length = 10)
    private String voie;

    @Column(name = "date_administration", nullable = false)
    private LocalDate dateAdministration;

    @Column(name = "seance_id")
    private UUID seanceId;

    @Column(name = "administre_par", length = 100)
    private String administrePar;

    @Column(name = "administree", nullable = false)
    private boolean administree;

    @Column(name = "motif_non_administration", length = 500)
    private String motifNonAdministration;

    @Column(name = "article_id")
    private UUID articleId;

    @Column(name = "quantite_article", precision = 10, scale = 3)
    private BigDecimal quantiteArticle;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

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

    public UUID getPrescriptionMedicaleId() {
        return prescriptionMedicaleId;
    }

    public void setPrescriptionMedicaleId(UUID prescriptionMedicaleId) {
        this.prescriptionMedicaleId = prescriptionMedicaleId;
    }

    public String getTypeTraitement() {
        return typeTraitement;
    }

    public void setTypeTraitement(String typeTraitement) {
        this.typeTraitement = typeTraitement;
    }

    public String getMolecule() {
        return molecule;
    }

    public void setMolecule(String molecule) {
        this.molecule = molecule;
    }

    public BigDecimal getDose() {
        return dose;
    }

    public void setDose(BigDecimal dose) {
        this.dose = dose;
    }

    public String getUniteDose() {
        return uniteDose;
    }

    public void setUniteDose(String uniteDose) {
        this.uniteDose = uniteDose;
    }

    public String getVoie() {
        return voie;
    }

    public void setVoie(String voie) {
        this.voie = voie;
    }

    public LocalDate getDateAdministration() {
        return dateAdministration;
    }

    public void setDateAdministration(LocalDate dateAdministration) {
        this.dateAdministration = dateAdministration;
    }

    public UUID getSeanceId() {
        return seanceId;
    }

    public void setSeanceId(UUID seanceId) {
        this.seanceId = seanceId;
    }

    public String getAdministrePar() {
        return administrePar;
    }

    public void setAdministrePar(String administrePar) {
        this.administrePar = administrePar;
    }

    public boolean isAdministree() {
        return administree;
    }

    public void setAdministree(boolean administree) {
        this.administree = administree;
    }

    public String getMotifNonAdministration() {
        return motifNonAdministration;
    }

    public void setMotifNonAdministration(String motifNonAdministration) {
        this.motifNonAdministration = motifNonAdministration;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public void setArticleId(UUID articleId) {
        this.articleId = articleId;
    }

    public BigDecimal getQuantiteArticle() {
        return quantiteArticle;
    }

    public void setQuantiteArticle(BigDecimal quantiteArticle) {
        this.quantiteArticle = quantiteArticle;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
