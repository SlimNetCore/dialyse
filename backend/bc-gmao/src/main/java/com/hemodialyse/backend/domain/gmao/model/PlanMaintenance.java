package com.hemodialyse.backend.domain.gmao.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité de domaine : Plan de Maintenance
 * Représente un plan de maintenance préventive pour un équipement
 * Aggregate root
 */
public class PlanMaintenance {

    private UUID id;
    private UUID equipementId;
    private UUID centreId;
    private String designation;
    private String description;
    private FrequenceMaintenance frequence;
    private StatutPlan statut;
    private LocalDateTime prochaineDatePrevue;
    private LocalDateTime derniereDateExecution;
    private Integer nombreExecutions;
    private String tachemesAEffectuer;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    private UUID creePar;
    private UUID modifiePar;

    // Constructeur privé pour DDD
    private PlanMaintenance() {
        this.nombreExecutions = 0;
    }

    /**
     * Crée un nouveau plan de maintenance
     */
    public static PlanMaintenance creer(
            UUID equipementId,
            UUID centreId,
            String designation,
            String description,
            FrequenceMaintenance frequence,
            LocalDateTime prochaineDatePrevue,
            String tachemesAEffectuer,
            UUID creePar) {

        if (equipementId == null) throw new IllegalArgumentException("Équipement requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (designation == null || designation.isBlank()) throw new IllegalArgumentException("Désignation requise");
        if (frequence == null) throw new IllegalArgumentException("Fréquence requise");
        if (prochaineDatePrevue == null) throw new IllegalArgumentException("Prochaine date prévue requise");

        PlanMaintenance plan = new PlanMaintenance();
        plan.id = UUID.randomUUID();
        plan.equipementId = equipementId;
        plan.centreId = centreId;
        plan.designation = designation;
        plan.description = description;
        plan.frequence = frequence;
        plan.statut = StatutPlan.ACTIF;
        plan.prochaineDatePrevue = prochaineDatePrevue;
        plan.tachemesAEffectuer = tachemesAEffectuer;
        plan.dateCreation = LocalDateTime.now();
        plan.creePar = creePar;
        plan.nombreExecutions = 0;

        return plan;
    }

    /**
     * Reconstruit un plan de maintenance depuis la persistance
     * À utiliser uniquement par les adapters de persistance
     */
    public static PlanMaintenance reconstruct(
            UUID id,
            UUID equipementId,
            UUID centreId,
            String designation,
            String description,
            FrequenceMaintenance frequence,
            StatutPlan statut,
            LocalDateTime prochaineDatePrevue,
            LocalDateTime derniereDateExecution,
            Integer nombreExecutions,
            String tachemesAEffectuer,
            LocalDateTime dateCreation,
            LocalDateTime dateModification,
            UUID creePar,
            UUID modifiePar) {

        PlanMaintenance plan = new PlanMaintenance();
        plan.id = id;
        plan.equipementId = equipementId;
        plan.centreId = centreId;
        plan.designation = designation;
        plan.description = description;
        plan.frequence = frequence;
        plan.statut = statut;
        plan.prochaineDatePrevue = prochaineDatePrevue;
        plan.derniereDateExecution = derniereDateExecution;
        plan.nombreExecutions = nombreExecutions;
        plan.tachemesAEffectuer = tachemesAEffectuer;
        plan.dateCreation = dateCreation;
        plan.dateModification = dateModification;
        plan.creePar = creePar;
        plan.modifiePar = modifiePar;

        return plan;
    }

    /**
     * Enregistre l'exécution du plan de maintenance
     */
    public void enregistrerExecution(UUID parUtilisateur) {
        if (this.statut != StatutPlan.ACTIF) {
            throw new IllegalStateException("Seul un plan actif peut être exécuté");
        }

        this.derniereDateExecution = LocalDateTime.now();
        this.nombreExecutions++;

        // Calcule la prochaine date en fonction de la fréquence
        this.prochaineDatePrevue = calculerProchaineDatePrevue();

        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Calcule la prochaine date prévue en fonction de la fréquence
     */
    private LocalDateTime calculerProchaineDatePrevue() {
        LocalDateTime now = LocalDateTime.now();
        return switch (frequence) {
            case MENSUELLE -> now.plusMonths(1);
            case TRIMESTRIELLE -> now.plusMonths(3);
            case SEMESTRIELLE -> now.plusMonths(6);
            case ANNUELLE -> now.plusYears(1);
            case BIMENSUELLE -> now.plusDays(15);
            case HEBDOMADAIRE -> now.plusWeeks(1);
        };
    }

    /**
     * Désactive le plan de maintenance
     */
    public void desactiver(UUID parUtilisateur) {
        if (this.statut != StatutPlan.ACTIF) {
            throw new IllegalStateException("Seul un plan actif peut être désactivé");
        }
        this.statut = StatutPlan.INACTIF;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Réactive le plan de maintenance
     */
    public void reactiver(LocalDateTime nouvelleDatePrevue, UUID parUtilisateur) {
        if (this.statut != StatutPlan.INACTIF) {
            throw new IllegalStateException("Seul un plan inactif peut être réactivé");
        }
        this.statut = StatutPlan.ACTIF;
        this.prochaineDatePrevue = nouvelleDatePrevue;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getEquipementId() {
        return equipementId;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public String getDesignation() {
        return designation;
    }

    public String getDescription() {
        return description;
    }

    public FrequenceMaintenance getFrequence() {
        return frequence;
    }

    public StatutPlan getStatut() {
        return statut;
    }

    public LocalDateTime getProchaineDatePrevue() {
        return prochaineDatePrevue;
    }

    public LocalDateTime getDerniereDateExecution() {
        return derniereDateExecution;
    }

    public Integer getNombreExecutions() {
        return nombreExecutions;
    }

    public String getTachemesAEffectuer() {
        return tachemesAEffectuer;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public LocalDateTime getDateModification() {
        return dateModification;
    }

    public UUID getCreePar() {
        return creePar;
    }

    public UUID getModifiePar() {
        return modifiePar;
    }
}


