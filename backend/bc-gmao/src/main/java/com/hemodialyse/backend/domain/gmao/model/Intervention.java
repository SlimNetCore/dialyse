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
    /**
     * États saisissables au démarrage : la réforme (REFORME) n'est jamais décidée par une intervention.
     */
    public static final Set<StatutEquipement> ETATS_AVANT = EnumSet.of(
            StatutEquipement.EN_SERVICE, StatutEquipement.EN_MAINTENANCE,
            StatutEquipement.EN_ATTENTE_PIECE, StatutEquipement.HORS_SERVICE);
    /**
     * États saisissables à la clôture : « À réformer » est une proposition, la décision de réforme
     * revient à la personne habilitée.
     */
    public static final Set<StatutEquipement> ETATS_APRES = EnumSet.of(
            StatutEquipement.EN_SERVICE, StatutEquipement.EN_ATTENTE_PIECE,
            StatutEquipement.HORS_SERVICE, StatutEquipement.A_REFORMER);
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    private UUID creePar;
    private UUID modifiePar;
    private StatutEquipement etatEquipementAvant;
    private StatutEquipement etatEquipementApres;

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
            StatutEquipement etatEquipementAvant,
            UUID creePar) {

        if (etatEquipementAvant == null || !ETATS_AVANT.contains(etatEquipementAvant)) {
            throw new IllegalArgumentException("État de l'équipement au moment de l'intervention invalide");
        }
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
        intervention.etatEquipementAvant = etatEquipementAvant;
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
            List<LigneCoutIntervention> lignesCout,
            StatutEquipement etatEquipementAvant,
            StatutEquipement etatEquipementApres) {

        Intervention intervention = new Intervention();
        intervention.etatEquipementAvant = etatEquipementAvant;
        intervention.etatEquipementApres = etatEquipementApres;
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
    public void terminer(String actions, StatutEquipement etatEquipementApres, UUID parUtilisateur) {
        if (this.statut != StatutIntervention.EN_COURS) {
            throw new IllegalStateException("Seule une intervention en cours peut être terminée");
        }
        if (actions == null || actions.isBlank()) {
            throw new IllegalArgumentException("Actions requises");
        }
        if (etatEquipementApres == null || !ETATS_APRES.contains(etatEquipementApres)) {
            throw new IllegalArgumentException("État de l'équipement après l'intervention invalide");
        }

        this.etatEquipementApres = etatEquipementApres;
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
     * À la clôture : valorise le temps de l'intervenant (tarif horaire × durée début→fin) en ligne de coût
     * {@link TypeLigneCout#INTERVENANT}. Sans effet si l'intervention n'est pas terminée, si le tarif est
     * absent/nul, si la durée est nulle, ou si une ligne INTERVENANT a déjà été saisie manuellement.
     *
     * @return true si une ligne a été ajoutée
     */
    public boolean appliquerTarifIntervenant(BigDecimal tarifHoraire, UUID parUtilisateur) {
        if (statut != StatutIntervention.TERMINEE || dateDebut == null || dateFin == null) return false;
        if (tarifHoraire == null || tarifHoraire.signum() <= 0) return false;
        if (lignesCout.stream().anyMatch(l -> l.getType() == TypeLigneCout.INTERVENANT)) return false;

        long minutes = java.time.Duration.between(dateDebut, dateFin).toMinutes();
        if (minutes <= 0) return false;
        BigDecimal heures = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        if (heures.signum() <= 0) return false;

        ajouterLigneCout(LigneCoutIntervention.creer(
                TypeLigneCout.INTERVENANT, "Temps intervenant", heures, tarifHoraire, null), parUtilisateur);
        return true;
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

    public StatutEquipement getEtatEquipementAvant() {
        return etatEquipementAvant;
    }

    public StatutEquipement getEtatEquipementApres() {
        return etatEquipementApres;
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


