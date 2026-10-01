package com.hemodialyse.backend.domain.gmao.model;

import com.hemodialyse.backend.domain.shared.vo.Money;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private OffsetDateTime dateDebut;
    private OffsetDateTime dateFin;
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
    private OffsetDateTime dateCreation;
    private OffsetDateTime dateModification;
    private UUID creePar;
    private UUID modifiePar;
    private StatutEquipement etatEquipementAvant;
    private StatutEquipement etatEquipementApres;
    private PrioriteIntervention priorite = PrioriteIntervention.NORMALE;
    private OffsetDateTime echeance;
    private String symptome;
    private String cause;
    private UUID demarrePar;
    private OffsetDateTime demarreLe;
    private UUID cloturePar;
    private OffsetDateTime clotureLe;
    private UUID annulePar;
    private OffsetDateTime annuleLe;

    // Constructeur privé pour DDD
    private Intervention() {
        this.taches = new ArrayList<>();
        this.lignesCout = new ArrayList<>();
    }

    /**
     * Crée une intervention sans suivi détaillé (priorité normale, sans échéance ni symptôme).
     */
    public static Intervention creer(
            UUID equipementId, UUID centreId, TypeIntervention type, OffsetDateTime dateDebut, String description,
            UUID intervenantId, StatutEquipement etatEquipementAvant, UUID creePar) {
        return creer(equipementId, centreId, type, dateDebut, description, intervenantId, etatEquipementAvant,
                null, PrioriteIntervention.NORMALE, null, creePar);
    }

    /**
     * Crée une nouvelle intervention
     */
    public static Intervention creer(
            UUID equipementId,
            UUID centreId,
            TypeIntervention type,
            OffsetDateTime dateDebut,
            String description,
            UUID intervenantId,
            StatutEquipement etatEquipementAvant,
            String symptome,
            PrioriteIntervention priorite,
            OffsetDateTime echeance,
            UUID creePar) {

        if (echeance != null && echeance.isBefore(dateDebut != null ? dateDebut : echeance)) {
            throw new IllegalArgumentException("L'échéance doit être postérieure à la date de début");
        }

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
        intervention.dateCreation = OffsetDateTime.now(ZoneOffset.UTC);
        intervention.symptome = blankToNull(symptome);
        intervention.priorite = priorite != null ? priorite : PrioriteIntervention.NORMALE;
        intervention.echeance = echeance;
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
            OffsetDateTime dateDebut,
            OffsetDateTime dateFin,
            UUID intervenantId,
            String description,
            String actions,
            String pieceRemplacee,
            String observations,
            OffsetDateTime dateCreation,
            OffsetDateTime dateModification,
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Marque l'intervention comme commencée
     */
    public void demarrer(UUID parUtilisateur) {
        if (this.statut != StatutIntervention.PLANIFIEE) {
            throw new IllegalStateException("Seule une intervention planifiée peut être démarrée");
        }
        // La date/heure de début reste celle saisie dans la fiche d'intervention.
        this.statut = StatutIntervention.EN_COURS;
        this.demarreLe = OffsetDateTime.now(ZoneOffset.UTC);
        this.demarrePar = parUtilisateur;
        this.dateModification = OffsetDateTime.now(ZoneOffset.UTC);
        this.modifiePar = parUtilisateur;
    }

    /**
     * Termine l'intervention sans préciser la cause de la panne.
     */
    public void terminer(String actions, StatutEquipement etatEquipementApres, OffsetDateTime dateFin, UUID parUtilisateur) {
        terminer(actions, etatEquipementApres, dateFin, null, parUtilisateur);
    }

    /**
     * Marque l'intervention comme terminée ; {@code cause} = cause de la panne trouvée à la clôture (optionnelle).
     */
    public void terminer(String actions, StatutEquipement etatEquipementApres, OffsetDateTime dateFin, String cause,
                         UUID parUtilisateur) {
        if (this.statut != StatutIntervention.EN_COURS) {
            throw new IllegalStateException("Seule une intervention en cours peut être terminée");
        }
        if (actions == null || actions.isBlank()) {
            throw new IllegalArgumentException("Actions requises");
        }
        if (etatEquipementApres == null || !ETATS_APRES.contains(etatEquipementApres)) {
            throw new IllegalArgumentException("État de l'équipement après l'intervention invalide");
        }
        if (dateFin == null) {
            throw new IllegalArgumentException("Date et heure de fin requises");
        }
        if (dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début");
        }

        this.etatEquipementApres = etatEquipementApres;
        this.statut = StatutIntervention.TERMINEE;
        this.dateFin = dateFin;
        this.actions = actions;
        this.cause = blankToNull(cause);
        this.clotureLe = OffsetDateTime.now(ZoneOffset.UTC);
        this.cloturePar = parUtilisateur;
        this.dateModification = OffsetDateTime.now(ZoneOffset.UTC);
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
     * Annule l'intervention
     */
    public void annuler(String raison, UUID parUtilisateur) {
        if (this.statut == StatutIntervention.TERMINEE) {
            throw new IllegalStateException("Une intervention terminée ne peut pas être annulée");
        }
        this.statut = StatutIntervention.ANNULEE;
        this.observations = (this.observations != null ? this.observations + "; " : "") + "Annulée: " + raison;
        this.annuleLe = OffsetDateTime.now(ZoneOffset.UTC);
        this.annulePar = parUtilisateur;
        this.dateModification = OffsetDateTime.now(ZoneOffset.UTC);
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
     * Ajoute une ligne de coût (pièce, main d'œuvre, intervenant...) — aide à la décision sur le coût
     * réel de maintenance.
     */
    public void ajouterLigneCout(LigneCoutIntervention ligne, UUID parUtilisateur) {
        if (ligne == null) throw new IllegalArgumentException("Ligne de coût requise");
        if (statut == StatutIntervention.ANNULEE) {
            throw new IllegalStateException("Une intervention annulée n'accepte plus de ligne de coût");
        }
        this.lignesCout.add(ligne);
        this.dateModification = OffsetDateTime.now(ZoneOffset.UTC);
        this.modifiePar = parUtilisateur;
    }

    /**
     * Restaure le suivi détaillé (priorité, échéance, symptôme/cause, audit du cycle de vie) depuis la
     * persistance — à utiliser uniquement par les adapters, à la suite de {@link #reconstruct}.
     */
    public Intervention restaurerSuivi(
            PrioriteIntervention priorite, OffsetDateTime echeance, String symptome, String cause,
            UUID demarrePar, OffsetDateTime demarreLe, UUID cloturePar, OffsetDateTime clotureLe,
            UUID annulePar, OffsetDateTime annuleLe) {
        this.priorite = priorite != null ? priorite : PrioriteIntervention.NORMALE;
        this.echeance = echeance;
        this.symptome = symptome;
        this.cause = cause;
        this.demarrePar = demarrePar;
        this.demarreLe = demarreLe;
        this.cloturePar = cloturePar;
        this.clotureLe = clotureLe;
        this.annulePar = annulePar;
        this.annuleLe = annuleLe;
        return this;
    }

    /**
     * Rappel : une intervention encore planifiée alors que son heure de début est passée.
     */
    public boolean enRetard(OffsetDateTime maintenant) {
        return statut == StatutIntervention.PLANIFIEE && dateDebut != null && dateDebut.isBefore(maintenant);
    }

    /**
     * Échéance dépassée alors que l'intervention n'est ni terminée ni annulée.
     */
    public boolean echeanceDepassee(OffsetDateTime maintenant) {
        return echeance != null && echeance.isBefore(maintenant)
                && (statut == StatutIntervention.PLANIFIEE || statut == StatutIntervention.EN_COURS);
    }

    /**
     * Ligne de temps : création, démarrage, clôture ou annulation, et dernière modification si postérieure.
     */
    public List<EvenementIntervention> chronologie() {
        List<EvenementIntervention> events = new ArrayList<>();
        events.add(new EvenementIntervention(EvenementIntervention.Type.CREEE, dateCreation, creePar));
        if (demarreLe != null) {
            events.add(new EvenementIntervention(EvenementIntervention.Type.DEMARREE, demarreLe, demarrePar));
        }
        if (clotureLe != null) {
            events.add(new EvenementIntervention(EvenementIntervention.Type.TERMINEE, clotureLe, cloturePar));
        }
        if (annuleLe != null) {
            events.add(new EvenementIntervention(EvenementIntervention.Type.ANNULEE, annuleLe, annulePar));
        }
        OffsetDateTime dernier = events.stream().map(EvenementIntervention::at)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        if (dateModification != null && (dernier == null || dateModification.isAfter(dernier))) {
            events.add(new EvenementIntervention(EvenementIntervention.Type.MODIFIEE, dateModification, modifiePar));
        }
        events.sort(Comparator.comparing(EvenementIntervention::at, Comparator.nullsFirst(Comparator.naturalOrder())));
        return events;
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

    public TypeIntervention getType() {
        return type;
    }

    public StatutIntervention getStatut() {
        return statut;
    }

    public OffsetDateTime getDateDebut() {
        return dateDebut;
    }

    public OffsetDateTime getDateFin() {
        return dateFin;
    }

    public UUID getDemarrePar() {
        return demarrePar;
    }

    public OffsetDateTime getDemarreLe() {
        return demarreLe;
    }

    public UUID getCloturePar() {
        return cloturePar;
    }

    public OffsetDateTime getClotureLe() {
        return clotureLe;
    }

    public UUID getAnnulePar() {
        return annulePar;
    }

    public OffsetDateTime getAnnuleLe() {
        return annuleLe;
    }

    public PrioriteIntervention getPriorite() {
        return priorite;
    }

    public OffsetDateTime getEcheance() {
        return echeance;
    }

    public String getSymptome() {
        return symptome;
    }

    public String getCause() {
        return cause;
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


