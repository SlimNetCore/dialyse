package com.hemodialyse.backend.infrastructure.persistence.entity.gmao;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
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

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateDebut;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateFin;

    @Column
    private UUID intervenantId;

    @Column(length = 50)
    private String etatEquipementAvant;

    @Column(length = 50)
    private String etatEquipementApres;

    @Column(length = 20)
    private String priorite;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime echeance;

    @Column(columnDefinition = "TEXT")
    private String symptome;

    @Column(columnDefinition = "TEXT")
    private String cause;

    @Column
    private UUID demarrePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime demarreLe;

    @Column
    private UUID cloturePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime clotureLe;

    @Column
    private UUID annulePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime annuleLe;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String actions;

    @Column(length = 255)
    private String pieceRemplacee;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateCreation;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dateModification;

    @Column(nullable = false, updatable = false)
    private UUID creePar;

    @Column
    private UUID modifiePar;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deletedAt;

    // Constructeurs
    public InterventionEntity() {
    }

    public InterventionEntity(UUID id, UUID equipementId, UUID centreId, String type,
                              String statut, OffsetDateTime dateDebut, OffsetDateTime dateFin,
                              UUID intervenantId, String description, String actions,
                              String pieceRemplacee, String observations,
                              OffsetDateTime dateCreation, OffsetDateTime dateModification,
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

    public OffsetDateTime getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(OffsetDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }

    public OffsetDateTime getDateFin() {
        return dateFin;
    }

    public void setDateFin(OffsetDateTime dateFin) {
        this.dateFin = dateFin;
    }

    public String getPriorite() {
        return priorite;
    }

    public void setPriorite(String priorite) {
        this.priorite = priorite;
    }

    public OffsetDateTime getEcheance() {
        return echeance;
    }

    public void setEcheance(OffsetDateTime echeance) {
        this.echeance = echeance;
    }

    public String getSymptome() {
        return symptome;
    }

    public void setSymptome(String symptome) {
        this.symptome = symptome;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public UUID getDemarrePar() {
        return demarrePar;
    }

    public void setDemarrePar(UUID demarrePar) {
        this.demarrePar = demarrePar;
    }

    public OffsetDateTime getDemarreLe() {
        return demarreLe;
    }

    public void setDemarreLe(OffsetDateTime demarreLe) {
        this.demarreLe = demarreLe;
    }

    public UUID getCloturePar() {
        return cloturePar;
    }

    public void setCloturePar(UUID cloturePar) {
        this.cloturePar = cloturePar;
    }

    public OffsetDateTime getClotureLe() {
        return clotureLe;
    }

    public void setClotureLe(OffsetDateTime clotureLe) {
        this.clotureLe = clotureLe;
    }

    public UUID getAnnulePar() {
        return annulePar;
    }

    public void setAnnulePar(UUID annulePar) {
        this.annulePar = annulePar;
    }

    public OffsetDateTime getAnnuleLe() {
        return annuleLe;
    }

    public void setAnnuleLe(OffsetDateTime annuleLe) {
        this.annuleLe = annuleLe;
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

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(OffsetDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public OffsetDateTime getDateModification() {
        return dateModification;
    }

    public void setDateModification(OffsetDateTime dateModification) {
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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

