package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité JPA pour PlanMaintenance GMAO
 */
@Entity
@Table(name = "gmao_plans_maintenance")
public class PlanMaintenanceEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID equipementId;

    @Column(nullable = false)
    private UUID centreId;

    @Column(nullable = false, length = 255)
    private String designation;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String frequence;

    @Column(nullable = false, length = 50)
    private String statut;

    @Column(nullable = false)
    private LocalDateTime prochaineDatePrevue;

    @Column
    private LocalDateTime derniereDateExecution;

    @Column(nullable = false)
    private Integer nombreExecutions;

    @Column(columnDefinition = "TEXT")
    private String tachesAEffectuer;

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
    public PlanMaintenanceEntity() {
    }

    public PlanMaintenanceEntity(UUID id, UUID equipementId, UUID centreId,
                                 String designation, String description, String frequence,
                                 String statut, LocalDateTime prochaineDatePrevue,
                                 LocalDateTime derniereDateExecution, Integer nombreExecutions,
                                 String tachesAEffectuer, LocalDateTime dateCreation,
                                 LocalDateTime dateModification, UUID creePar, UUID modifiePar) {
        this.id = id;
        this.equipementId = equipementId;
        this.centreId = centreId;
        this.designation = designation;
        this.description = description;
        this.frequence = frequence;
        this.statut = statut;
        this.prochaineDatePrevue = prochaineDatePrevue;
        this.derniereDateExecution = derniereDateExecution;
        this.nombreExecutions = nombreExecutions;
        this.tachesAEffectuer = tachesAEffectuer;
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

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFrequence() {
        return frequence;
    }

    public void setFrequence(String frequence) {
        this.frequence = frequence;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public LocalDateTime getProchaineDatePrevue() {
        return prochaineDatePrevue;
    }

    public void setProchaineDatePrevue(LocalDateTime prochaineDatePrevue) {
        this.prochaineDatePrevue = prochaineDatePrevue;
    }

    public LocalDateTime getDerniereDateExecution() {
        return derniereDateExecution;
    }

    public void setDerniereDateExecution(LocalDateTime derniereDateExecution) {
        this.derniereDateExecution = derniereDateExecution;
    }

    public Integer getNombreExecutions() {
        return nombreExecutions;
    }

    public void setNombreExecutions(Integer nombreExecutions) {
        this.nombreExecutions = nombreExecutions;
    }

    public String getTachesAEffectuer() {
        return tachesAEffectuer;
    }

    public void setTachesAEffectuer(String tachesAEffectuer) {
        this.tachesAEffectuer = tachesAEffectuer;
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

