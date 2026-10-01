package com.hemodialyse.backend.domain.gmao.model;

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
            UUID creePar) {

        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (designation == null || designation.isBlank()) throw new IllegalArgumentException("Désignation requise");
        if (type == null) throw new IllegalArgumentException("Type d'équipement requis");
        if (dateInstallation == null) throw new IllegalArgumentException("Date d'installation requise");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");

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
            UUID modifiePar) {

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
            UUID parUtilisateur) {
        if (designation == null || designation.isBlank()) {
            throw new IllegalArgumentException("Désignation requise");
        }
        this.designation = designation;
        this.fabricant = fabricant;
        this.modele = modele;
        this.numeroSerie = numeroSerie;
        this.localisation = localisation;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Marque l'équipement comme hors service
     */
    public void marquerHorsService(String raison, UUID parUtilisateur) {
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
        if (this.statut != StatutEquipement.HORS_SERVICE) {
            throw new IllegalStateException("Seul un équipement hors service peut être réactivé");
        }
        this.statut = StatutEquipement.EN_SERVICE;
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
}


