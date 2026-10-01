package com.hemodialyse.backend.domain.gmao.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private OffsetDateTime prochaineDatePrevue;
    private OffsetDateTime derniereDateExecution;
    private Integer nombreExecutions;
    private String tachemesAEffectuer;
    private OffsetDateTime dateCreation;
    private OffsetDateTime dateModification;
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
            OffsetDateTime prochaineDatePrevue,
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
        plan.dateCreation = OffsetDateTime.now(ZoneOffset.UTC);
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
            OffsetDateTime prochaineDatePrevue,
            OffsetDateTime derniereDateExecution,
            Integer nombreExecutions,
            String tachemesAEffectuer,
            OffsetDateTime dateCreation,
            OffsetDateTime dateModification,
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

        this.derniereDateExecution = OffsetDateTime.now(ZoneOffset.UTC);
        this.nombreExecutions++;

        // Calcule la prochaine date en fonction de la fréquence
        this.prochaineDatePrevue = calculerProchaineDatePrevue();

        this.dateModification = OffsetDateTime.now(ZoneOffset.UTC);
        this.modifiePar = parUtilisateur;
    }

    /**
     * Calcule la prochaine date prévue en fonction de la fréquence
     */
    private OffsetDateTime calculerProchaineDatePrevue() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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
        this.dateModification = OffsetDateTime.now(ZoneOffset.UTC);
        this.modifiePar = parUtilisateur;
    }

    /**
     * Réactive le plan de maintenance
     */
    public void reactiver(OffsetDateTime nouvelleDatePrevue, UUID parUtilisateur) {
        if (this.statut != StatutPlan.INACTIF) {
            throw new IllegalStateException("Seul un plan inactif peut être réactivé");
        }
        this.statut = StatutPlan.ACTIF;
        this.prochaineDatePrevue = nouvelleDatePrevue;
        this.dateModification = OffsetDateTime.now(ZoneOffset.UTC);
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

    public OffsetDateTime getProchaineDatePrevue() {
        return prochaineDatePrevue;
    }

    public OffsetDateTime getDerniereDateExecution() {
        return derniereDateExecution;
    }

    public Integer getNombreExecutions() {
        return nombreExecutions;
    }

    public String getTachemesAEffectuer() {
        return tachemesAEffectuer;
    }

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }

    public OffsetDateTime getDateModification() {
        return dateModification;
    }

    public UUID getCreePar() {
        return creePar;
    }

    public UUID getModifiePar() {
        return modifiePar;
    }
}


