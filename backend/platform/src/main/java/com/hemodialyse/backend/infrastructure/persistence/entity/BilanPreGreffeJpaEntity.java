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
@Table(name = "bilans_pre_greffe")
public class BilanPreGreffeJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "statut", nullable = false, length = 30)
    private String statut;

    @Column(name = "date_debut_bilan")
    private LocalDate dateDebutBilan;

    @Column(name = "date_inscription_liste_attente")
    private LocalDate dateInscriptionListeAttente;

    @Column(name = "date_greffe")
    private LocalDate dateGreffe;

    @Column(name = "groupe_sanguin_confirme", length = 5)
    private String groupeSanguinConfirme;

    @Column(name = "typage_hla", length = 500)
    private String typageHla;

    @Column(name = "pra_classe_i", precision = 5, scale = 2)
    private BigDecimal praClasseI;

    @Column(name = "pra_classe_ii", precision = 5, scale = 2)
    private BigDecimal praClasseII;

    @Column(name = "contre_indications", columnDefinition = "TEXT")
    private String contreIndications;

    @Column(name = "conclusion_nephrologue", columnDefinition = "TEXT")
    private String conclusionNephrologue;

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

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public LocalDate getDateDebutBilan() {
        return dateDebutBilan;
    }

    public void setDateDebutBilan(LocalDate dateDebutBilan) {
        this.dateDebutBilan = dateDebutBilan;
    }

    public LocalDate getDateInscriptionListeAttente() {
        return dateInscriptionListeAttente;
    }

    public void setDateInscriptionListeAttente(LocalDate dateInscriptionListeAttente) {
        this.dateInscriptionListeAttente = dateInscriptionListeAttente;
    }

    public LocalDate getDateGreffe() {
        return dateGreffe;
    }

    public void setDateGreffe(LocalDate dateGreffe) {
        this.dateGreffe = dateGreffe;
    }

    public String getGroupeSanguinConfirme() {
        return groupeSanguinConfirme;
    }

    public void setGroupeSanguinConfirme(String groupeSanguinConfirme) {
        this.groupeSanguinConfirme = groupeSanguinConfirme;
    }

    public String getTypageHla() {
        return typageHla;
    }

    public void setTypageHla(String typageHla) {
        this.typageHla = typageHla;
    }

    public BigDecimal getPraClasseI() {
        return praClasseI;
    }

    public void setPraClasseI(BigDecimal praClasseI) {
        this.praClasseI = praClasseI;
    }

    public BigDecimal getPraClasseII() {
        return praClasseII;
    }

    public void setPraClasseII(BigDecimal praClasseII) {
        this.praClasseII = praClasseII;
    }

    public String getContreIndications() {
        return contreIndications;
    }

    public void setContreIndications(String contreIndications) {
        this.contreIndications = contreIndications;
    }

    public String getConclusionNephrologue() {
        return conclusionNephrologue;
    }

    public void setConclusionNephrologue(String conclusionNephrologue) {
        this.conclusionNephrologue = conclusionNephrologue;
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
