package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Modèle de persistance d'un plan de maintenance : table {@code gmao_plans_maintenance}, la table historique, avec les
 * mêmes noms de colonnes que l'ancienne entité (ADR 0024). Jamais exposé hors de {@code infrastructure.persistence} ;
 * le domaine reste sans ORM.
 */
@Entity
@Table(name = "gmao_plans_maintenance")
public class PlanMaintenanceJpa {

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

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant prochaineDatePrevue;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant derniereDateExecution;

    @Column(nullable = false)
    private int nombreExecutions;

    // KB-GMAO-21 : la colonne lue et écrite historiquement est « tachesaeffectuer » (nommage Boot), pas « taches_a_effectuer ».
    @Column(name = "tachesaeffectuer", columnDefinition = "TEXT")
    private String tachesAEffectuer;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant dateCreation;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant dateModification;

    @Column(nullable = false, updatable = false)
    private UUID creePar;

    private UUID modifiePar;

    /** Suppression logique posée hors du domaine (SQL) ; effacée à la réécriture du plan (KB-GMAO-17, reproduit). */
    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant deletedAt;

    protected PlanMaintenanceJpa() {
    }

    public PlanMaintenanceJpa(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
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

    public Instant getProchaineDatePrevue() {
        return prochaineDatePrevue;
    }

    public void setProchaineDatePrevue(Instant prochaineDatePrevue) {
        this.prochaineDatePrevue = prochaineDatePrevue;
    }

    public Instant getDerniereDateExecution() {
        return derniereDateExecution;
    }

    public void setDerniereDateExecution(Instant derniereDateExecution) {
        this.derniereDateExecution = derniereDateExecution;
    }

    public int getNombreExecutions() {
        return nombreExecutions;
    }

    public void setNombreExecutions(int nombreExecutions) {
        this.nombreExecutions = nombreExecutions;
    }

    public String getTachesAEffectuer() {
        return tachesAEffectuer;
    }

    public void setTachesAEffectuer(String tachesAEffectuer) {
        this.tachesAEffectuer = tachesAEffectuer;
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
}
