package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Entité JPA pour Intervention GMAO
 */
@Entity
@Table(name = "gmao_interventions")
public class InterventionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID equipementId;

    @Column(nullable = false)
    private UUID centreId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 50)
    private String statut;

    @Column(nullable = false)
    private LocalDateTime dateDebut;

    @Column
    private LocalDateTime dateFin;

    @Column
    private UUID intervenantId;

    @Column(length = 50)
    private String etatEquipementAvant;

    @Column(length = 50)
    private String etatEquipementApres;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String actions;

    @Column(length = 255)
    private String pieceRemplacee;

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

    // Constructeurs
    public InterventionEntity() {
    }

    public InterventionEntity(UUID id, UUID equipementId, UUID centreId, String type,
                              String statut, LocalDateTime dateDebut, LocalDateTime dateFin,
                              UUID intervenantId, String description, String actions,
                              String pieceRemplacee, String observations,
                              LocalDateTime dateCreation, LocalDateTime dateModification,
                              UUID creePar, UUID modifiePar,
                              String etatEquipementAvant, String etatEquipementApres) {
        this.etatEquipementAvant = etatEquipementAvant;
        this.etatEquipementApres = etatEquipementApres;
        this.id = id;
        this.equipementId = equipementId;
        this.centreId = centreId;
        this.type = type;
        this.statut = statut;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.intervenantId = intervenantId;
        this.description = description;
        this.actions = actions;
        this.pieceRemplacee = pieceRemplacee;
        this.observations = observations;
        this.dateCreation = dateCreation;
        this.dateModification = dateModification;
        this.creePar = creePar;
        this.modifiePar = modifiePar;
    }

    // Getters et Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEquipementId() {
        return equipementId;
    }

    public void setEquipementId(UUID equipementId) {
        this.equipementId = equipementId;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public void setCentreId(UUID centreId) {
        this.centreId = centreId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDateTime dateFin) {
        this.dateFin = dateFin;
    }

    public String getEtatEquipementAvant() {
        return etatEquipementAvant;
    }

    public String getEtatEquipementApres() {
        return etatEquipementApres;
    }

    public UUID getIntervenantId() {
        return intervenantId;
    }

    public void setIntervenantId(UUID intervenantId) {
        this.intervenantId = intervenantId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public String getPieceRemplacee() {
        return pieceRemplacee;
    }

    public void setPieceRemplacee(String pieceRemplacee) {
        this.pieceRemplacee = pieceRemplacee;
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
}

