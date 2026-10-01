package com.hemodialyse.backend.domain.gmao.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Entité de domaine : Équipement
 * Représente un équipement GMAO (générateur de dialyse, station de traitement d'eau, etc.)
 * Aggregate root
 */
public class Equipement {

    private UUID id;
    private String code;
    private String designation;
    private TypeEquipement type;
    private String fabricant;
    private String modele;
    private String numeroSerie;
    private LocalDateTime dateInstallation;
    private UUID centreId;
    private StatutEquipement statut;
    private String localisation;
    private String observations;
    private UUID salleId;
    private BigDecimal prixAcquisition;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    private UUID creePar;
    private UUID modifiePar;

    // Constructeur privé pour DDD
    private Equipement() {
    }

    /**
     * Crée un nouvel équipement
     */
    public static Equipement creer(
            String code,
            String designation,
            TypeEquipement type,
            String fabricant,
            String modele,
            String numeroSerie,
            LocalDateTime dateInstallation,
            UUID centreId,
            String localisation,
            UUID creePar,
            UUID salleId,
            BigDecimal prixAcquisition) {

        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (designation == null || designation.isBlank()) throw new IllegalArgumentException("Désignation requise");
        if (type == null) throw new IllegalArgumentException("Type d'équipement requis");
        if (dateInstallation == null) throw new IllegalArgumentException("Date d'installation requise");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (prixAcquisition != null && prixAcquisition.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le prix d'acquisition ne peut pas être négatif");
        }

        Equipement equipement = new Equipement();
        equipement.id = UUID.randomUUID();
        equipement.code = code;
        equipement.designation = designation;
        equipement.type = type;
        equipement.fabricant = fabricant;
        equipement.modele = modele;
        equipement.numeroSerie = numeroSerie;
        equipement.dateInstallation = dateInstallation;
        equipement.centreId = centreId;
        equipement.statut = StatutEquipement.EN_SERVICE;
        equipement.localisation = localisation;
        equipement.salleId = salleId;
        equipement.prixAcquisition = prixAcquisition;
        equipement.dateCreation = LocalDateTime.now();
        equipement.creePar = creePar;

        return equipement;
    }

    /**
     * Reconstruit un équipement depuis la persistance
     * À utiliser uniquement par les adapters de persistance
     */
    public static Equipement reconstruct(
            UUID id,
            String code,
            String designation,
            TypeEquipement type,
            String fabricant,
            String modele,
            String numeroSerie,
            LocalDateTime dateInstallation,
            UUID centreId,
            StatutEquipement statut,
            String localisation,
            String observations,
            LocalDateTime dateCreation,
            LocalDateTime dateModification,
            UUID creePar,
            UUID modifiePar,
            UUID salleId,
            BigDecimal prixAcquisition) {

        Equipement equipement = new Equipement();
        equipement.id = id;
        equipement.code = code;
        equipement.designation = designation;
        equipement.type = type;
        equipement.fabricant = fabricant;
        equipement.modele = modele;
        equipement.numeroSerie = numeroSerie;
        equipement.dateInstallation = dateInstallation;
        equipement.centreId = centreId;
        equipement.statut = statut;
        equipement.localisation = localisation;
        equipement.observations = observations;
        equipement.dateCreation = dateCreation;
        equipement.dateModification = dateModification;
        equipement.creePar = creePar;
        equipement.modifiePar = modifiePar;
        equipement.salleId = salleId;
        equipement.prixAcquisition = prixAcquisition;

        return equipement;
    }

    /**
     * Modifie les caractéristiques modifiables de l'équipement (le code et le type, structurants,
     * restent immuables après création).
     */
    public void modifier(
            String designation,
            String fabricant,
            String modele,
            String numeroSerie,
            String localisation,
            UUID parUtilisateur,
            UUID salleId,
            BigDecimal prixAcquisition) {
        if (designation == null || designation.isBlank()) {
            throw new IllegalArgumentException("Désignation requise");
        }
        if (prixAcquisition != null && prixAcquisition.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le prix d'acquisition ne peut pas être négatif");
        }
        this.designation = designation;
        this.fabricant = fabricant;
        this.modele = modele;
        this.numeroSerie = numeroSerie;
        this.localisation = localisation;
        this.salleId = salleId;
        this.prixAcquisition = prixAcquisition;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Marque l'équipement comme hors service
     */
    public void marquerHorsService(String raison, UUID parUtilisateur) {
        if (this.statut == StatutEquipement.REFORME) {
            throw new IllegalStateException("Un équipement réformé ne peut plus changer de statut");
        }
        if (this.statut == StatutEquipement.HORS_SERVICE) {
            throw new IllegalStateException("L'équipement est déjà hors service");
        }
        this.statut = StatutEquipement.HORS_SERVICE;
        this.observations = (this.observations != null ? this.observations + "; " : "") + "Hors service: " + raison;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Réactive l'équipement
     */
    public void reactiver(UUID parUtilisateur) {
        if (this.statut == StatutEquipement.REFORME) {
            throw new IllegalStateException("Un équipement réformé ne peut pas être réactivé");
        }
        if (this.statut != StatutEquipement.HORS_SERVICE) {
            throw new IllegalStateException("Seul un équipement hors service peut être réactivé");
        }
        this.statut = StatutEquipement.EN_SERVICE;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Change l'état de l'équipement suite à une intervention (état constaté au démarrage / après clôture).
     * La réforme (REFORME) et la désactivation ne passent jamais par ici : la réforme est réservée à
     * {@link #reformer} (personne habilitée).
     *
     * @return true si le statut a effectivement changé
     */
    public boolean changerStatutIntervention(StatutEquipement nouveau, String motif, UUID parUtilisateur) {
        if (nouveau == null) throw new IllegalArgumentException("Nouvel état requis");
        if (this.statut == StatutEquipement.REFORME) {
            throw new IllegalStateException("Un équipement réformé ne peut plus changer de statut");
        }
        if (nouveau == StatutEquipement.REFORME || nouveau == StatutEquipement.DESACTIF) {
            throw new IllegalArgumentException("Cet état ne peut pas être fixé par une intervention");
        }
        if (nouveau == this.statut) return false;
        this.statut = nouveau;
        this.observations = (this.observations != null ? this.observations + "; " : "") + motif;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
        return true;
    }

    /**
     * Réforme définitivement l'équipement (fin de vie — état terminal, aucun retour en arrière possible).
     */
    public void reformer(String motif, UUID parUtilisateur) {
        if (this.statut == StatutEquipement.REFORME) {
            throw new IllegalStateException("L'équipement est déjà réformé");
        }
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Motif de réforme requis");
        }
        this.statut = StatutEquipement.REFORME;
        this.observations = (this.observations != null ? this.observations + "; " : "") + "Réformé: " + motif;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Met à jour les observations
     */
    public void ajouterObservation(String observation, UUID parUtilisateur) {
        if (observation == null || observation.isBlank()) {
            throw new IllegalArgumentException("Observation requise");
        }
        this.observations = (this.observations != null ? this.observations + "\n" : "") +
                LocalDateTime.now() + " - " + observation;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDesignation() {
        return designation;
    }

    public TypeEquipement getType() {
        return type;
    }

    public String getFabricant() {
        return fabricant;
    }

    public String getModele() {
        return modele;
    }

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public LocalDateTime getDateInstallation() {
        return dateInstallation;
    }

    public UUID getCentreId() {
        return centreId;
    }

    public StatutEquipement getStatut() {
        return statut;
    }

    public String getLocalisation() {
        return localisation;
    }

    public String getObservations() {
        return observations;
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

    public UUID getSalleId() {
        return salleId;
    }

    public BigDecimal getPrixAcquisition() {
        return prixAcquisition;
    }
}


