package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Intervention de maintenance sur un équipement (racine d'agrégat) : de la planification à la clôture, rectifiable, avec
 * son coût. Garantit le cycle {@code PLANIFIEE -> EN_COURS -> TERMINEE} avec annulation (R10), les états de l'équipement
 * relevés avant et après (R11, R12), l'échéance (R13), la clôture (R14), la rectification (R15), les lignes de coût
 * (R16, R17, R18) et la valorisation automatique du temps de l'intervenant (R19). L'heure arrive par paramètre ; aucune
 * dépendance technique. L'équipement et l'intervenant ne sont référencés que par identité.
 * <p>
 * Comportements du legacy reproduits (ADR 0019) : annuler une intervention déjà annulée est accepté et cumule les raisons
 * (KB-GMAO-48) ; la valorisation ne dépend que des dates de l'intervention, les secondes étant tronquées (KB-GMAO-43) ; les
 * montants gardent l'échelle de leurs facteurs (KB-GMAO-44) ; la rectification conserve les actions et la cause (KB-GMAO-51).
 * Les tâches (R21) sont une fonctionnalité nouvelle, créée à la demande de l'utilisateur (KB-GMAO-55) : au plus
 * {@link #LIMITE_DE_TACHES}, ajoutées sur une intervention ouverte, démarrées et achevées sur une intervention en cours.
 */
public final class Intervention {

    /** États saisissables avant l'intervention : la réforme n'est jamais décidée par une intervention (R11). */
    /** Nombre maximal de tâches d'une intervention (à valider par le métier). */
    public static final int LIMITE_DE_TACHES = 30;

    private static final Set<StatutEquipement> ETATS_AVANT = EnumSet.of(StatutEquipement.EN_SERVICE,
            StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_ATTENTE_PIECE, StatutEquipement.HORS_SERVICE);

    /** États saisissables après : « à réformer » n'est qu'une proposition, la décision revient à la personne habilitée (R12). */
    private static final Set<StatutEquipement> ETATS_APRES = EnumSet.of(StatutEquipement.EN_SERVICE,
            StatutEquipement.EN_ATTENTE_PIECE, StatutEquipement.HORS_SERVICE, StatutEquipement.A_REFORMER);

    private final InterventionId id;
    private final EquipementId equipementId;
    private final CentreId centreId;
    private final TypeIntervention type;
    private final IntervenantId intervenantId;
    private final String description;
    private final StatutEquipement etatAvant;
    private final PrioriteIntervention priorite;
    private final Instant echeance;
    private final String symptome;
    private final String pieceRemplacee;
    private final Jalon creation;

    private StatutIntervention statut;
    private Instant debut;
    private Instant fin;
    private String actions;
    private String cause;
    private String observations;
    private StatutEquipement etatApres;
    private Jalon modification;
    private Jalon demarrage;
    private Jalon cloture;
    private Jalon annulation;
    private final List<LigneDeCout> lignes;
    private final List<Rectification> rectifications;
    private final List<Tache> taches;

    private final List<EvenementDIntervention> evenements = new ArrayList<>();

    private Intervention(InterventionId id, EquipementId equipementId, CentreId centreId, TypeIntervention type,
                         StatutIntervention statut, Instant debut, Instant fin, IntervenantId intervenantId,
                         String description, String actions, String pieceRemplacee, String observations,
                         StatutEquipement etatAvant, StatutEquipement etatApres, PrioriteIntervention priorite,
                         Instant echeance, String symptome, String cause, Jalon creation, Jalon modification,
                         Jalon demarrage, Jalon cloture, Jalon annulation, List<LigneDeCout> lignes,
                         List<Rectification> rectifications, List<Tache> taches) {
        this.id = id;
        this.equipementId = equipementId;
        this.centreId = centreId;
        this.type = type;
        this.statut = statut;
        this.debut = debut;
        this.fin = fin;
        this.intervenantId = intervenantId;
        this.description = description;
        this.actions = actions;
        this.pieceRemplacee = pieceRemplacee;
        this.observations = observations;
        this.etatAvant = etatAvant;
        this.etatApres = etatApres;
        this.priorite = priorite;
        this.echeance = echeance;
        this.symptome = symptome;
        this.cause = cause;
        this.creation = creation;
        this.modification = modification;
        this.demarrage = demarrage;
        this.cloture = cloture;
        this.annulation = annulation;
        this.lignes = new ArrayList<>(lignes);
        this.rectifications = new ArrayList<>(rectifications);
        this.taches = new ArrayList<>(taches);
    }

    /**
     * Planifie une intervention. L'intervenant n'est pas contrôlé (KB-GMAO-42) ; la description est conservée telle que
     * reçue, le symptôme est rogné (vide : absent) ; la priorité vaut {@code NORMALE} par défaut.
     */
    public static Intervention planifier(InterventionId id, EquipementId equipementId, CentreId centreId,
                                         TypeIntervention type, Instant debut, String description,
                                         IntervenantId intervenantId, StatutEquipement etatAvant, String symptome,
                                         PrioriteIntervention priorite, Instant echeance, UtilisateurId par,
                                         Instant maintenant) {
        if (echeance != null && echeance.isBefore(debut != null ? debut : echeance)) {
            throw new ChampInvalide("L'échéance doit être postérieure à la date de début");
        }
        if (etatAvant == null || !ETATS_AVANT.contains(etatAvant)) {
            throw new ChampInvalide("État de l'équipement au moment de l'intervention invalide");
        }
        if (type == null) throw new ChampInvalide("Type d'intervention requis");
        if (debut == null) throw new ChampInvalide("Date de début requise");
        if (description == null || description.isBlank()) throw new ChampInvalide("Description requise");

        Intervention intervention = new Intervention(id, equipementId, centreId, type, StatutIntervention.PLANIFIEE, debut,
                null, intervenantId, description, null, null, null, etatAvant, null,
                priorite != null ? priorite : PrioriteIntervention.NORMALE, echeance, rogneOuAbsent(symptome), null,
                new Jalon(par, maintenant), null, null, null, null, List.of(), List.of(), List.of());
        intervention.evenements.add(new InterventionPlanifiee(id, equipementId, centreId, type, debut, par, maintenant));
        return intervention;
    }

    /** Reconstitution depuis la persistance (adapters seulement) : aucun événement, aucune règle rejouée. */
    public static Intervention reconstituer(InterventionId id, EquipementId equipementId, CentreId centreId,
                                            TypeIntervention type, StatutIntervention statut, Instant debut, Instant fin,
                                            IntervenantId intervenantId, String description, String actions,
                                            String pieceRemplacee, String observations, StatutEquipement etatAvant,
                                            StatutEquipement etatApres, PrioriteIntervention priorite, Instant echeance,
                                            String symptome, String cause, Jalon creation, Jalon modification,
                                            Jalon demarrage, Jalon cloture, Jalon annulation, List<LigneDeCout> lignes,
                                            List<Rectification> rectifications) {
        return reconstituer(id, equipementId, centreId, type, statut, debut, fin, intervenantId, description, actions,
                pieceRemplacee, observations, etatAvant, etatApres, priorite, echeance, symptome, cause, creation,
                modification, demarrage, cloture, annulation, lignes, rectifications, List.of());
    }

    /** Reconstitution avec les tâches (adapters seulement). */
    public static Intervention reconstituer(InterventionId id, EquipementId equipementId, CentreId centreId,
                                            TypeIntervention type, StatutIntervention statut, Instant debut, Instant fin,
                                            IntervenantId intervenantId, String description, String actions,
                                            String pieceRemplacee, String observations, StatutEquipement etatAvant,
                                            StatutEquipement etatApres, PrioriteIntervention priorite, Instant echeance,
                                            String symptome, String cause, Jalon creation, Jalon modification,
                                            Jalon demarrage, Jalon cloture, Jalon annulation, List<LigneDeCout> lignes,
                                            List<Rectification> rectifications, List<Tache> taches) {
        return new Intervention(id, equipementId, centreId, type, statut, debut, fin, intervenantId, description, actions,
                pieceRemplacee, observations, etatAvant, etatApres, priorite, echeance, symptome, cause, creation,
                modification, demarrage, cloture, annulation, lignes, rectifications, taches);
    }

    /** Démarre une intervention planifiée. */
    public void demarrer(UtilisateurId par, Instant maintenant) {
        if (statut != StatutIntervention.PLANIFIEE) throw StatutIncompatible.pourDemarrer();
        statut = StatutIntervention.EN_COURS;
        demarrage = new Jalon(par, maintenant);
        toucher(par, maintenant);
        evenements.add(new InterventionDemarree(id, equipementId, centreId, par, maintenant));
    }

    /**
     * Clôture une intervention en cours. {@code conditions} (nature et tarif de l'intervenant, lus à la clôture) servent à
     * valoriser son temps (R19) ; {@code nouvelleLigne} est l'identité de la ligne automatique si elle est ajoutée.
     * Rien n'est modifié si la clôture est refusée.
     */
    public void terminer(String actions, StatutEquipement etatApres, Instant fin, String cause,
                         ConditionsDeValorisation conditions, LigneDeCoutId nouvelleLigne, UtilisateurId par,
                         Instant maintenant) {
        if (statut != StatutIntervention.EN_COURS) throw StatutIncompatible.pourTerminer();
        if (actions == null || actions.isBlank()) throw new ChampInvalide("Actions requises");
        if (etatApres == null || !ETATS_APRES.contains(etatApres)) {
            throw new ChampInvalide("État de l'équipement après l'intervention invalide");
        }
        if (fin == null) throw new ChampInvalide("Date et heure de fin requises");
        if (fin.isBefore(debut)) throw new ChampInvalide("La date de fin doit être postérieure à la date de début");

        this.etatApres = etatApres;
        this.statut = StatutIntervention.TERMINEE;
        this.fin = fin;
        this.actions = actions;
        this.cause = rogneOuAbsent(cause);
        this.cloture = new Jalon(par, maintenant);
        toucher(par, maintenant);
        valoriserLeTemps(conditions, nouvelleLigne);
        evenements.add(new InterventionTerminee(id, equipementId, centreId, type, debut, fin, coutTotal(),
                rectifications.size() + 1, par, maintenant));
    }

    /** Annule l'intervention, même si elle l'est déjà : les raisons se cumulent (KB-GMAO-48). Une terminée ne s'annule pas. */
    public void annuler(String raison, UtilisateurId par, Instant maintenant) {
        if (statut == StatutIntervention.TERMINEE) throw StatutIncompatible.pourAnnuler();
        if (raison == null || raison.isBlank()) throw new ChampInvalide("Raison requise");
        statut = StatutIntervention.ANNULEE;
        observations = (observations != null ? observations + "; " : "") + "Annulée: " + raison;
        annulation = new Jalon(par, maintenant);
        toucher(par, maintenant);
        evenements.add(new InterventionAnnulee(id, equipementId, centreId, raison, par, maintenant));
    }

    /**
     * Rectifie une intervention terminée : la rouvre (en cours), conserve la clôture précédente dans la trace, retire les
     * lignes automatiques (recalculées à la nouvelle clôture) ; les actions et la cause sont conservées. Une nouvelle date
     * de début peut être donnée ; elle n'est contrôlée qu'à la clôture suivante. L'équipement n'est pas touché.
     */
    public void rectifier(String motif, Instant nouveauDebut, UUID idDeLaRectification, UtilisateurId par,
                          Instant maintenant) {
        if (statut != StatutIntervention.TERMINEE) throw StatutIncompatible.pourRectifier();
        if (motif == null || motif.isBlank()) throw new ChampInvalide("Motif de rectification requis");
        String motifRogne = motif.trim();
        rectifications.add(new Rectification(idDeLaRectification, motifRogne, par, maintenant,
                cloture == null ? null : cloture.le(), cloture == null ? null : cloture.par()));
        if (nouveauDebut != null) {
            debut = nouveauDebut;
        }
        statut = StatutIntervention.EN_COURS;
        fin = null;
        cloture = null;
        etatApres = null;
        lignes.removeIf(LigneDeCout::estAutomatique);
        toucher(par, maintenant);
        evenements.add(new InterventionRectifiee(id, equipementId, centreId, motifRogne, par, maintenant));
    }

    /**
     * Ajoute une ligne de coût. R20 : pas de main d'œuvre saisie pour un intervenant externe ({@code natureDeLIntervenant},
     * celle de l'intervenant désigné s'il appartient au centre, sinon {@code null}). Refusé aussi si l'intervention est
     * annulée ; une terminée en accepte (KB-GMAO-45).
     */
    public void ajouterLigneDeCout(LigneDeCout ligne, NatureIntervenant natureDeLIntervenant, UtilisateurId par,
                                   Instant maintenant) {
        if (ligne == null) throw new ChampInvalide("Ligne de coût requise");
        if (ligne.type() == TypeLigneCout.MAIN_OEUVRE && natureDeLIntervenant == NatureIntervenant.EXTERNE) {
            throw new MainOeuvreRefuseePourExterne();
        }
        if (statut == StatutIntervention.ANNULEE) throw StatutIncompatible.pourAjouterUneLigne();
        lignes.add(ligne);
        toucher(par, maintenant);
    }

    /** Retire une ligne de coût d'une intervention ouverte (planifiée ou en cours) ; une terminée se rectifie d'abord (R17). */
    public void retirerLigneDeCout(LigneDeCoutId ligne, UtilisateurId par, Instant maintenant) {
        if (statut != StatutIntervention.PLANIFIEE && statut != StatutIntervention.EN_COURS) {
            throw StatutIncompatible.pourRetirerUneLigne();
        }
        if (!lignes.removeIf(l -> l.id().equals(ligne))) throw new LigneDeCoutInconnue();
        toucher(par, maintenant);
    }

    /**
     * Ajoute une tâche à faire (R21) : intervention planifiée ou en cours, description requise, au plus
     * {@link #LIMITE_DE_TACHES} tâches. Rien n'est modifié si l'ajout est refusé.
     */
    public void ajouterTache(TacheId tacheId, String description, UtilisateurId par, Instant maintenant) {
        if (statut != StatutIntervention.PLANIFIEE && statut != StatutIntervention.EN_COURS) {
            throw StatutIncompatible.pourAjouterUneTache();
        }
        if (taches.size() >= LIMITE_DE_TACHES) throw new LimiteDeTachesAtteinte();
        taches.add(Tache.ajouter(tacheId, description, new Jalon(par, maintenant)));
        toucher(par, maintenant);
        evenements.add(new TacheAjoutee(id, tacheId, equipementId, centreId, par, maintenant));
    }

    /** Démarre une tâche à faire (R21) ; l'intervention doit être en cours. */
    public void demarrerTache(TacheId tacheId, UtilisateurId par, Instant maintenant) {
        Tache tache = tacheEnCoursDeTravail(tacheId);
        tache.demarrer(new Jalon(par, maintenant));
        toucher(par, maintenant);
        evenements.add(new TacheDemarree(id, tacheId, equipementId, centreId, par, maintenant));
    }

    /** Achève une tâche en cours (R21) ; l'intervention doit être en cours. */
    public void acheverTache(TacheId tacheId, UtilisateurId par, Instant maintenant) {
        Tache tache = tacheEnCoursDeTravail(tacheId);
        tache.achever(new Jalon(par, maintenant));
        toucher(par, maintenant);
        evenements.add(new TacheAchevee(id, tacheId, equipementId, centreId, par, maintenant));
    }

    private Tache tacheEnCoursDeTravail(TacheId tacheId) {
        if (statut != StatutIntervention.EN_COURS) throw StatutIncompatible.pourTravaillerUneTache();
        return taches.stream().filter(t -> t.id().equals(tacheId)).findFirst().orElseThrow(TacheInconnue::new);
    }

    /** Planifiée dont l'heure de début est passée (R27). */
    public boolean enRetard(Instant maintenant) {
        return statut == StatutIntervention.PLANIFIEE && debut.isBefore(maintenant);
    }

    /** Échéance dépassée alors que l'intervention n'est ni terminée ni annulée (R27). */
    public boolean echeanceDepassee(Instant maintenant) {
        return echeance != null && echeance.isBefore(maintenant)
                && (statut == StatutIntervention.PLANIFIEE || statut == StatutIntervention.EN_COURS);
    }

    /** Somme des lignes (R18) : zéro, d'échelle nulle, sans ligne ; sinon l'échelle la plus fine des montants. */
    public BigDecimal coutTotal() {
        return lignes.stream().map(LigneDeCout::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Retire et retourne les événements levés depuis la dernière publication. */
    public List<EvenementDIntervention> tirerEvenements() {
        List<EvenementDIntervention> tires = List.copyOf(evenements);
        evenements.clear();
        return tires;
    }

    /**
     * R19 : durée en minutes entières (les secondes sont tronquées), en heures arrondies au centième demi supérieur, fois le
     * tarif. Honoraires de l'intervenant pour un externe, main d'œuvre pour un interne. Rien si le tarif est absent ou nul,
     * si la durée est nulle, ou si une ligne de la même nature existe déjà (jamais de double comptage).
     */
    private void valoriserLeTemps(ConditionsDeValorisation conditions, LigneDeCoutId nouvelleLigne) {
        if (conditions == null || !conditions.valorise()) return;
        TypeLigneCout nature = conditions.externe() ? TypeLigneCout.INTERVENANT : TypeLigneCout.MAIN_OEUVRE;
        if (lignes.stream().anyMatch(l -> l.type() == nature)) return;
        long minutes = Duration.between(debut, fin).toMinutes();
        if (minutes <= 0) return;
        BigDecimal heures = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        lignes.add(LigneDeCout.calculer(nouvelleLigne, nature,
                conditions.externe() ? "Honoraires intervenant" : "Main d'œuvre", heures, conditions.tarifHoraire()));
    }

    private void toucher(UtilisateurId par, Instant maintenant) {
        modification = new Jalon(par, maintenant);
    }

    private static String rogneOuAbsent(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }

    public InterventionId id() {
        return id;
    }

    public EquipementId equipementId() {
        return equipementId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public TypeIntervention type() {
        return type;
    }

    public StatutIntervention statut() {
        return statut;
    }

    public Instant debut() {
        return debut;
    }

    public Instant fin() {
        return fin;
    }

    /** Intervenant désigné à la planification, jamais contrôlé (KB-GMAO-42), ou {@code null}. */
    public IntervenantId intervenantId() {
        return intervenantId;
    }

    public String description() {
        return description;
    }

    public String actions() {
        return actions;
    }

    /** Jamais alimentée par une commande (KB-GMAO-55) : lue en base seulement. */
    public String pieceRemplacee() {
        return pieceRemplacee;
    }

    public String observations() {
        return observations;
    }

    public StatutEquipement etatAvant() {
        return etatAvant;
    }

    public StatutEquipement etatApres() {
        return etatApres;
    }

    public PrioriteIntervention priorite() {
        return priorite;
    }

    public Instant echeance() {
        return echeance;
    }

    public String symptome() {
        return symptome;
    }

    public String cause() {
        return cause;
    }

    public Jalon creation() {
        return creation;
    }

    /** Dernière modification, ou {@code null} tant que rien n'a bougé depuis la planification. */
    public Jalon modification() {
        return modification;
    }

    public Jalon demarrage() {
        return demarrage;
    }

    /** Clôture en vigueur : {@code null} avant la clôture et après une rectification. */
    public Jalon cloture() {
        return cloture;
    }

    public Jalon annulation() {
        return annulation;
    }

    public List<LigneDeCout> lignes() {
        return List.copyOf(lignes);
    }

    /** Tâches dans l'ordre d'ajout. */
    public List<Tache> taches() {
        return List.copyOf(taches);
    }

    public List<Rectification> rectifications() {
        return List.copyOf(rectifications);
    }

    /**
     * Chronologie (RG-GMA-031) : création, démarrage, clôture, annulation, et pour chaque rectification la clôture qu'elle a
     * annulée puis la rectification elle-même ; la dernière modification s'ajoute si elle est postérieure à tous ces
     * événements (KB-GMAO-79 : l'ajout d'une tâche ou d'une ligne de coût la fait apparaître, reproduit). Du plus ancien au
     * plus récent, à égalité dans l'ordre de construction ; les tâches n'y figurent pas.
     */
    public List<EvenementDeChronologie> chronologie() {
        List<EvenementDeChronologie> evenements = new ArrayList<>();
        ajouter(evenements, EvenementDeChronologie.Type.CREEE, creation);
        ajouter(evenements, EvenementDeChronologie.Type.DEMARREE, demarrage);
        ajouter(evenements, EvenementDeChronologie.Type.TERMINEE, cloture);
        ajouter(evenements, EvenementDeChronologie.Type.ANNULEE, annulation);
        for (Rectification r : rectifications) {
            if (r.clotureAnterieureLe() != null) {
                evenements.add(new EvenementDeChronologie(EvenementDeChronologie.Type.TERMINEE, r.clotureAnterieureLe(),
                        r.clotureAnterieurePar(), null));
            }
            evenements.add(new EvenementDeChronologie(EvenementDeChronologie.Type.RECTIFIEE, r.le(), r.par(), r.motif()));
        }
        Instant dernier = evenements.stream().map(EvenementDeChronologie::le).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        if (modification != null && modification.le() != null && (dernier == null || modification.le().isAfter(dernier))) {
            ajouter(evenements, EvenementDeChronologie.Type.MODIFIEE, modification);
        }
        evenements.sort(Comparator.comparing(EvenementDeChronologie::le, Comparator.nullsFirst(Comparator.naturalOrder())));
        return evenements;
    }

    private static void ajouter(List<EvenementDeChronologie> evenements, EvenementDeChronologie.Type type, Jalon jalon) {
        if (jalon != null && jalon.le() != null) {
            evenements.add(new EvenementDeChronologie(type, jalon.le(), jalon.par(), null));
        }
    }
}
