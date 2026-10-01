package com.hemodialyse.backend.domain.gmao.model;

import com.hemodialyse.backend.domain.shared.vo.Money;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;

/**
 * Entité de domaine : Intervention GMAO
 * Représente une intervention de maintenance sur un équipement
 * Aggregate root
 */
public class Intervention {

    private UUID id;
    private UUID equipementId;
    private UUID centreId;
    private TypeIntervention type;
    private StatutIntervention statut;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private UUID intervenantId;
    private String description;
    private String actions;
    private String pieceRemplacee;
    private String observations;
    private List<TacheIntervention> taches;
    private List<LigneCoutIntervention> lignesCout;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    private UUID creePar;
    private UUID modifiePar;

    // Constructeur privé pour DDD
    private Intervention() {
        this.taches = new ArrayList<>();
        this.lignesCout = new ArrayList<>();
    }

    /**
     * Crée une nouvelle intervention
     */
    public static Intervention creer(
            UUID equipementId,
            UUID centreId,
            TypeIntervention type,
            LocalDateTime dateDebut,
            String description,
            UUID intervenantId,
            UUID creePar) {

        if (equipementId == null) throw new IllegalArgumentException("Équipement requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (type == null) throw new IllegalArgumentException("Type d'intervention requis");
        if (dateDebut == null) throw new IllegalArgumentException("Date de début requise");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Description requise");

        Intervention intervention = new Intervention();
        intervention.id = UUID.randomUUID();
        intervention.equipementId = equipementId;
        intervention.centreId = centreId;
        intervention.type = type;
        intervention.statut = StatutIntervention.PLANIFIEE;
        intervention.dateDebut = dateDebut;
        intervention.description = description;
        intervention.intervenantId = intervenantId;
        intervention.dateCreation = LocalDateTime.now();
        intervention.creePar = creePar;
        intervention.taches = new ArrayList<>();
        intervention.lignesCout = new ArrayList<>();

        return intervention;
    }

    /**
     * Reconstruit une intervention depuis la persistance
     * À utiliser uniquement par les adapters de persistance
     */
    public static Intervention reconstruct(
            UUID id,
            UUID equipementId,
            UUID centreId,
            TypeIntervention type,
            StatutIntervention statut,
            LocalDateTime dateDebut,
            LocalDateTime dateFin,
            UUID intervenantId,
            String description,
            String actions,
            String pieceRemplacee,
            String observations,
            LocalDateTime dateCreation,
            LocalDateTime dateModification,
            UUID creePar,
            UUID modifiePar,
            List<LigneCoutIntervention> lignesCout) {

        Intervention intervention = new Intervention();
        intervention.id = id;
        intervention.equipementId = equipementId;
        intervention.centreId = centreId;
        intervention.type = type;
        intervention.statut = statut;
        intervention.dateDebut = dateDebut;
        intervention.dateFin = dateFin;
        intervention.intervenantId = intervenantId;
        intervention.description = description;
        intervention.actions = actions;
        intervention.pieceRemplacee = pieceRemplacee;
        intervention.observations = observations;
        intervention.dateCreation = dateCreation;
        intervention.dateModification = dateModification;
        intervention.creePar = creePar;
        intervention.modifiePar = modifiePar;
        intervention.lignesCout = lignesCout == null ? new ArrayList<>() : new ArrayList<>(lignesCout);

        return intervention;
    }

    /**
     * Marque l'intervention comme commencée
     */
    public void demarrer(UUID parUtilisateur) {
        if (this.statut != StatutIntervention.PLANIFIEE) {
            throw new IllegalStateException("Seule une intervention planifiée peut être démarrée");
        }
        this.statut = StatutIntervention.EN_COURS;
        this.dateDebut = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Marque l'intervention comme terminée
     */
    public void terminer(String actions, UUID parUtilisateur) {
        if (this.statut != StatutIntervention.EN_COURS) {
            throw new IllegalStateException("Seule une intervention en cours peut être terminée");
        }
        if (actions == null || actions.isBlank()) {
            throw new IllegalArgumentException("Actions requises");
        }

        this.statut = StatutIntervention.TERMINEE;
        this.dateFin = LocalDateTime.now();
        this.actions = actions;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Annule l'intervention
     */
    public void annuler(String raison, UUID parUtilisateur) {
        if (this.statut == StatutIntervention.TERMINEE) {
            throw new IllegalStateException("Une intervention terminée ne peut pas être annulée");
        }
        this.statut = StatutIntervention.ANNULEE;
        this.observations = (this.observations != null ? this.observations + "; " : "") + "Annulée: " + raison;
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Ajoute une tâche à l'intervention
     */
    public void ajouterTache(TacheIntervention tache) {
        if (tache == null) throw new IllegalArgumentException("Tâche requise");
        this.taches.add(tache);
    }

    /**
     * Ajoute une ligne de coût (pièce, main d'œuvre, intervenant...) — aide à la décision sur le coût
     * réel de maintenance.
     */
    public void ajouterLigneCout(LigneCoutIntervention ligne, UUID parUtilisateur) {
        if (ligne == null) throw new IllegalArgumentException("Ligne de coût requise");
        this.lignesCout.add(ligne);
        this.dateModification = LocalDateTime.now();
        this.modifiePar = parUtilisateur;
    }

    /**
     * Coût total de l'intervention (somme des lignes de coût).
     */
    public BigDecimal coutTotal() {
        return lignesCout.stream()
                .map(l -> Money.of(l.montant()))
                .reduce(Money.zero(), Money::add)
                .amount();
    }

    /**
     * Définit la pièce remplacée
     */
    public void definirPieceRemplacee(String piece, UUID parUtilisateur) {
        this.pieceRemplacee = piece;
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

    public TypeIntervention getType() {
        return type;
    }

    public StatutIntervention getStatut() {
        return statut;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public UUID getIntervenantId() {
        return intervenantId;
    }

    public String getDescription() {
        return description;
    }

    public String getActions() {
        return actions;
    }

    public String getPieceRemplacee() {
        return pieceRemplacee;
    }

    public String getObservations() {
        return observations;
    }

    public List<TacheIntervention> getTaches() {
        return new ArrayList<>(taches);
    }

    public List<LigneCoutIntervention> getLignesCout() {
        return new ArrayList<>(lignesCout);
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


