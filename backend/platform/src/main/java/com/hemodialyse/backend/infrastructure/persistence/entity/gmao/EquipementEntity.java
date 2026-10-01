package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Entité JPA pour Equipement GMAO
 */
@Entity
@Table(name = "gmao_equipements",
        uniqueConstraints = @UniqueConstraint(name = "uk_gmao_equipement_centre_code", columnNames = {"centreId", "code"}))
public class EquipementEntity {

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

    @Column(nullable = false)
    private LocalDateTime dateInstallation;

    @Column(nullable = false)
    private UUID centreId;

    @Column(nullable = false, length = 50)
    private String statut;

    @Column(length = 255)
    private String localisation;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column
    private LocalDateTime dateModification;

    @Column(nullable = false, updatable = false)
    private UUID creePar;

    @Column
    private UUID modifiePar;

    @Column
    private LocalDateTime deletedAt;

    @Column
    private UUID salleId;

    @Column(precision = 14, scale = 2)
    private BigDecimal prixAcquisition;

    // Constructeurs
    public EquipementEntity() {
    }

    public EquipementEntity(UUID id, String code, String designation, String type,
                            String fabricant, String modele, String numeroSerie,
                            LocalDateTime dateInstallation, UUID centreId, String statut,
                            String localisation, String observations, LocalDateTime dateCreation,
                            LocalDateTime dateModification, UUID creePar, UUID modifiePar,
                            UUID salleId, BigDecimal prixAcquisition) {
        this.id = id;
        this.code = code;
        this.designation = designation;
        this.type = type;
        this.fabricant = fabricant;
        this.modele = modele;
        this.numeroSerie = numeroSerie;
        this.dateInstallation = dateInstallation;
        this.centreId = centreId;
        this.statut = statut;
        this.localisation = localisation;
        this.observations = observations;
        this.dateCreation = dateCreation;
        this.dateModification = dateModification;
        this.creePar = creePar;
        this.modifiePar = modifiePar;
        this.salleId = salleId;
        this.prixAcquisition = prixAcquisition;
    }

    // Getters et Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public LocalDateTime getDateInstallation() {
        return dateInstallation;
    }

    public void setDateInstallation(LocalDateTime dateInstallation) {
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

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDateModification() {
        return dateModification;
    }

    public void setDateModification(LocalDateTime dateModification) {
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
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

