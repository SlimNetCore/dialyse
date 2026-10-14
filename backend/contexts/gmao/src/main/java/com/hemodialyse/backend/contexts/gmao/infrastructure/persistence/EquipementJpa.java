package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Modèle de persistance d'un équipement : table {@code gmao_equipements}, la table historique, avec les mêmes noms de
 * colonnes, longueurs et contrainte d'unicité (centre, code) que l'ancienne entité (ADR 0024) : la base refuse les valeurs
 * trop longues et le code d'un équipement supprimé logiquement (KB-GMAO-33/35), arrondit le prix à deux décimales. Jamais
 * exposé hors de {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "gmao_equipements",
        uniqueConstraints = @UniqueConstraint(name = "uk_gmao_equipement_centre_code", columnNames = {"centreId", "code"}))
public class EquipementJpa {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String designation;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(length = 100)
    private String fabricant;

    @Column(length = 100)
    private String modele;

    @Column(length = 100)
    private String numeroSerie;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant dateInstallation;

    @Column(nullable = false)
    private UUID centreId;

    @Column(nullable = false, length = 50)
    private String statut;

    @Column(length = 255)
    private String localisation;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant dateCreation;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant dateModification;

    @Column(nullable = false, updatable = false)
    private UUID creePar;

    @Column
    private UUID modifiePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant deletedAt;

    @Column
    private UUID salleId;

    @Column(precision = 14, scale = 2)
    private BigDecimal prixAcquisition;

    protected EquipementJpa() {
    }

    public EquipementJpa(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFabricant() {
        return fabricant;
    }

    public void setFabricant(String fabricant) {
        this.fabricant = fabricant;
    }

    public String getModele() {
        return modele;
    }

    public void setModele(String modele) {
        this.modele = modele;
    }

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public void setNumeroSerie(String numeroSerie) {
        this.numeroSerie = numeroSerie;
    }

    public Instant getDateInstallation() {
        return dateInstallation;
    }

    public void setDateInstallation(Instant dateInstallation) {
        this.dateInstallation = dateInstallation;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public void setCentreId(UUID centreId) {
        this.centreId = centreId;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getLocalisation() {
        return localisation;
    }

    public void setLocalisation(String localisation) {
        this.localisation = localisation;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(Instant dateCreation) {
        this.dateCreation = dateCreation;
    }

    public Instant getDateModification() {
        return dateModification;
    }

    public void setDateModification(Instant dateModification) {
        this.dateModification = dateModification;
    }

    public UUID getCreePar() {
        return creePar;
    }

    public void setCreePar(UUID creePar) {
        this.creePar = creePar;
    }

    public UUID getModifiePar() {
        return modifiePar;
    }

    public void setModifiePar(UUID modifiePar) {
        this.modifiePar = modifiePar;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public UUID getSalleId() {
        return salleId;
    }

    public void setSalleId(UUID salleId) {
        this.salleId = salleId;
    }

    public BigDecimal getPrixAcquisition() {
        return prixAcquisition;
    }

    public void setPrixAcquisition(BigDecimal prixAcquisition) {
        this.prixAcquisition = prixAcquisition;
    }
}
