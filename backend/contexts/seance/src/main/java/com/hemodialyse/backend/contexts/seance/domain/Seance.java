package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Séance : acte de dialyse d'un patient, un jour donné, dans un centre (racine d'agrégat, une seule entité). SE1 porte la CRÉATION
 * (statut {@code CREE}, éventuellement confirmée hors planning) ; SE2 la validation par l'infirmier, la signature du médecin et le
 * déverrouillage pour régularisation ; SE5 l'imposition d'un forfait (surcharge) ; SE6 le changement de jour et la suppression (refus
 * des séances facturées ou rattachées à une facture). La POSE du verrou de facturation reste à la Facturation : la Séance lit le statut
 * {@code FACTUREE} et {@code facture_id}, elle ne les écrit jamais.
 * <p>
 * Aucune règle d'unicité par patient et par jour : plusieurs séances le même jour restent permises (KB-SEA-02, reproduit).
 */
public final class Seance {

    private final SeanceId id;
    private final CentreId centre;
    private final PatientId patient;
    private LocalDate jour;
    private StatutDeSeance statut;
    private final Instant creeLe;
    private final DerogationHorsPlanning derogation;
    private Jalons jalons;
    private Traces traces;
    private ForfaitImpose forfaitImpose;
    private final FactureId facture;

    private Seance(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut, Instant creeLe,
                   DerogationHorsPlanning derogation, Jalons jalons, Traces traces) {
        this(id, centre, patient, jour, statut, creeLe, derogation, jalons, traces, null, null);
    }

    private Seance(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut, Instant creeLe,
                   DerogationHorsPlanning derogation, Jalons jalons, Traces traces, ForfaitImpose forfaitImpose, FactureId facture) {
        this.facture = facture;
        this.id = exiger(id, "Séance requise");
        this.centre = exiger(centre, "Centre requis");
        this.patient = exiger(patient, "Patient requis");
        this.jour = exiger(jour, "Jour de la séance requis");
        this.statut = exiger(statut, "Statut requis");
        this.creeLe = creeLe;
        this.derogation = derogation;
        this.jalons = jalons == null ? Jalons.AUCUN : jalons;
        this.traces = traces == null ? Traces.AUCUNE : traces;
        this.forfaitImpose = forfaitImpose;
    }

    /** Crée une séance programmée (patient attendu) : statut {@code CREE}, sans dérogation. */
    public static Seance creer(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, Instant creeLe) {
        return new Seance(id, centre, patient, jour, StatutDeSeance.CREE, exiger(creeLe, "Heure de création requise"), null, null, null);
    }

    /** Crée une séance confirmée hors planning : le motif (et sa précision) est inscrit sur la séance. */
    public static Seance creerHorsPlanning(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, Instant creeLe,
                                           DerogationHorsPlanning derogation) {
        return new Seance(id, centre, patient, jour, StatutDeSeance.CREE, exiger(creeLe, "Heure de création requise"),
                exiger(derogation, "Dérogation requise"), null, null);
    }

    /** Restitue une ligne existante (adapter de persistance) : aucune règle de création n'est rejouée. */
    public static Seance restituer(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut,
                                   Instant creeLe, DerogationHorsPlanning derogation, Jalons jalons) {
        return new Seance(id, centre, patient, jour, statut, creeLe, derogation, jalons, null);
    }

    /** Restitue une ligne existante avec ses traces (signataires, déverrouilleur, place mémorisée). */
    public static Seance restituer(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut,
                                   Instant creeLe, DerogationHorsPlanning derogation, Jalons jalons, Traces traces) {
        return new Seance(id, centre, patient, jour, statut, creeLe, derogation, jalons, traces);
    }

    /** Restitue une ligne existante avec ses traces et le forfait imposé (nul s'il n'y en a pas). */
    public static Seance restituer(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut,
                                   Instant creeLe, DerogationHorsPlanning derogation, Jalons jalons, Traces traces,
                                   ForfaitImpose forfaitImpose) {
        return new Seance(id, centre, patient, jour, statut, creeLe, derogation, jalons, traces, forfaitImpose, null);
    }

    /** Restitue une ligne existante complète, avec la facture qui l'a prise (nulle s'il n'y en a pas). */
    public static Seance restituer(SeanceId id, CentreId centre, PatientId patient, LocalDate jour, StatutDeSeance statut,
                                   Instant creeLe, DerogationHorsPlanning derogation, Jalons jalons, Traces traces,
                                   ForfaitImpose forfaitImpose, FactureId facture) {
        return new Seance(id, centre, patient, jour, statut, creeLe, derogation, jalons, traces, forfaitImpose, facture);
    }

    public SeanceId id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public PatientId patient() {
        return patient;
    }

    public LocalDate jour() {
        return jour;
    }

    public StatutDeSeance statut() {
        return statut;
    }

    /** Heure de création ; peut être nulle sur une ligne historique (colonne sans contrainte). */
    public Instant creeLe() {
        return creeLe;
    }

    public boolean horsPlanning() {
        return derogation != null;
    }

    public Optional<DerogationHorsPlanning> derogation() {
        return Optional.ofNullable(derogation);
    }

    public Jalons jalons() {
        return jalons;
    }

    /** Vrai tant que la séance n'a pas été validée par l'infirmier (le scan la valide alors, la secrétaire la laisse). */
    public boolean attendLaValidation() {
        return statut == StatutDeSeance.CREE;
    }

    public Traces traces() {
        return traces;
    }

    public boolean estDeverrouilleePourRegularisation() {
        return jalons.deverrouilleePourRegularisationLe() != null;
    }

    /**
     * Valide la séance par l'infirmier : {@code CREE} devient {@code VALIDEE}, la validation et la signature de l'infirmier sont datées
     * et le signataire retenu. Une séance déjà validée ou signée n'est pas rejetée et ne change pas (rend faux) ; facturée : refusée
     * (422 {@code SEANCE_FACTUREE_NON_MODIFIABLE}) ; tout autre statut (le reliquat {@code ABSENT}) : refusée.
     *
     * @return vrai si la séance vient de passer à validée
     */
    public boolean validerParLInfirmier(Instant maintenant, Signataire signataire) {
        exigerNonFacturee();
        if (statut == StatutDeSeance.VALIDEE || statut == StatutDeSeance.SIGNEE) return false;
        if (statut != StatutDeSeance.CREE) {
            throw new SeanceRefusee(MotifDeRefus.STATUT_NON_VALIDABLE, "La seance n'est pas en statut CREE");
        }
        this.statut = StatutDeSeance.VALIDEE;
        this.jalons = new Jalons(maintenant, maintenant, jalons.signeeParLeMedecinLe(), jalons.deverrouilleePourRegularisationLe());
        this.traces = new Traces(signataire.valeur(), traces.medecin(), traces.deverrouilleePar(), traces.place());
        return true;
    }

    /** Fige la place où la séance a eu lieu ; une place déjà mémorisée n'est jamais remplacée. */
    public void memoriserPlace(PlaceDeSeance placeDuJour) {
        if (traces.place() == null && placeDuJour != null) {
            this.traces = new Traces(traces.infirmier(), traces.medecin(), traces.deverrouilleePar(), placeDuJour);
        }
    }

    /**
     * Signature du médecin : seule une séance validée se signe (le message est le même pour toute autre cause, KB-SEA-20, reproduit) ;
     * une ligne incohérente (validée mais déjà signée) est refusée.
     */
    public void signerParLeMedecin(Instant maintenant, Signataire signataire) {
        exigerNonFacturee();
        if (statut != StatutDeSeance.VALIDEE) {
            throw new SeanceRefusee(MotifDeRefus.SIGNATURE_AVANT_VALIDATION,
                    "La signature medecin est autorisee uniquement apres validation infirmiere");
        }
        if (jalons.signeeParLeMedecinLe() != null) {
            throw new SeanceRefusee(MotifDeRefus.DEJA_SIGNEE, "La seance est deja signee par un medecin");
        }
        this.jalons = new Jalons(jalons.valideeLe(), jalons.signeeParLInfirmierLe(), maintenant, jalons.deverrouilleePourRegularisationLe());
        this.traces = new Traces(traces.infirmier(), signataire.valeur(), traces.deverrouilleePar(), traces.place());
        this.statut = StatutDeSeance.SIGNEE;
    }

    /**
     * L'administrateur déverrouille une séance « créée » d'un jour passé (validation oubliée) : le statut et la place ne changent pas.
     * Sans effet (rend faux) si elle l'est déjà. Le jour passé n'est borné à aucune fenêtre (KB-SEA-13, reproduit).
     *
     * @param aujourdhui le jour du centre (KB-SEA-01)
     */
    public boolean deverrouillerPourRegularisation(Instant maintenant, Signataire auteur, LocalDate aujourdhui) {
        exigerNonFacturee();
        if (statut != StatutDeSeance.CREE) {
            throw new SeanceRefusee(MotifDeRefus.DEVERROUILLAGE_INVALIDE,
                    "Seule une séance restée « créée » peut être déverrouillée pour régularisation");
        }
        if (!jour.isBefore(aujourdhui)) {
            throw new SeanceRefusee(MotifDeRefus.DEVERROUILLAGE_INVALIDE,
                    "Seule une séance d'un jour passé peut être déverrouillée pour régularisation");
        }
        if (estDeverrouilleePourRegularisation()) return false;
        this.jalons = new Jalons(jalons.valideeLe(), jalons.signeeParLInfirmierLe(), jalons.signeeParLeMedecinLe(), maintenant);
        this.traces = new Traces(traces.infirmier(), traces.medecin(), auteur.valeur(), traces.place());
        return true;
    }

    /**
     * Verrou des jours passés (évalué AVANT le statut et l'idempotence, KB-SEA-12) : l'infirmier ne valide pas une séance d'un jour passé
     * sans le déverrouillage de l'administrateur ; l'administrateur valide toujours ; une séance à venir se valide sans limite (reproduit).
     */
    public void exigerLaRegularisation(LocalDate aujourdhui, boolean administrateur) {
        if (jour.isBefore(aujourdhui) && !administrateur && !estDeverrouilleePourRegularisation()) {
            throw new SeanceRefusee(MotifDeRefus.REGULARISATION_NON_DEVERROUILLEE,
                    "Cette séance d'un jour passé doit d'abord être déverrouillée par l'administrateur");
        }
    }

    /**
     * Garde de l'AJOUT d'un consommable (SE3) : une séance facturée est refusée (422 {@code SEANCE_FACTUREE_NON_MODIFIABLE}), puis seule une
     * séance validée ou signée en reçoit ; {@code CREE} et le reliquat {@code ABSENT} sont refusés (422 {@code BUSINESS_RULE_VIOLATION}).
     */
    public void exigerLAjoutDeConsommables() {
        exigerNonFacturee();
        if (statut != StatutDeSeance.VALIDEE && statut != StatutDeSeance.SIGNEE) {
            throw new SeanceRefusee(MotifDeRefus.CONSOMMABLES_AVANT_VALIDATION, "La seance doit etre validee avant d'ajouter un consommable");
        }
    }

    /**
     * Garde de la MODIFICATION et du RETRAIT d'un consommable (SE3) : seule la séance facturée est refusée. Les statuts {@code CREE} et
     * {@code ABSENT} sont acceptés, contrairement à l'ajout (KB-SEA-24, reproduit : l'incohérence entre les trois routes est à faire
     * confirmer par les métiers avant la mise en production).
     */
    public void exigerLaModificationDesConsommables() {
        exigerNonFacturee();
    }

    /** Le forfait imposé à la séance, s'il y en a un. */
    public Optional<ForfaitImpose> forfaitImpose() {
        return Optional.ofNullable(forfaitImpose);
    }

    /**
     * Impose un forfait du référentiel à la séance (SE5) : copie son code, son nom et son prix tels quels, date l'imposition et retient
     * l'auteur ; remplace sans historique l'imposition précédente (KB-SEA-45, reproduit). Seule une séance facturée est refusée (422
     * {@code SEANCE_FACTUREE_NON_MODIFIABLE}) ; tout autre statut est accepté, une séance absente ou non validée comprise, et
     * {@code facture_id} n'est pas regardé (KB-SEA-43, reproduit : risque d'argent, condition de mise en production de l'ADR 0092).
     */
    public void imposerUnForfait(ForfaitDuCentre forfait, Instant maintenant, AuteurDuForfaitImpose auteur) {
        exigerNonFacturee();
        exiger(forfait, "Forfait requis");
        exiger(maintenant, "Instant de l'imposition requis");
        exiger(auteur, "Auteur requis");
        this.forfaitImpose = new ForfaitImpose(forfait.id(), forfait.code(), forfait.nom(), forfait.prix(), maintenant, auteur.valeur());
    }

    /** La facture qui a pris la séance (posée par la Facturation), s'il y en a une. */
    public Optional<FactureId> facture() {
        return Optional.ofNullable(facture);
    }

    /**
     * Change le jour de la séance (SE6). Seule la séance facturée est refusée (422 {@code SEANCE_FACTUREE_NON_MODIFIABLE}). AUCUNE autre
     * règle, reproduite du legacy et à confirmer métiers avant la mise en production (ADR 0093) : ni le statut (KB-SEA-54 : le jour d'une
     * séance validée ou signée, qui détermine sa période de facturation, est modifiable), ni le verrou des jours passés (KB-SEA-55), ni
     * calendrier, admission ou planning (KB-SEA-56). Le statut, les signatures, la place, le forfait imposé et la facture sont conservés.
     */
    public void changerDeJour(LocalDate nouveauJour) {
        exigerNonFacturee();
        this.jour = exiger(nouveauJour, "Jour de la séance requis");
    }

    /**
     * Garde de la SUPPRESSION : une séance facturée est refusée (422 {@code SEANCE_FACTUREE_NON_SUPPRIMABLE}, message du legacy), puis,
     * CORRECTION de KB-SEA-50 (à confirmer métiers), une séance rattachée à une facture l'est aussi quel que soit son statut (422
     * {@code SEANCE_RATTACHEE_A_UNE_FACTURE_NON_SUPPRIMABLE}) : la facture ne perd plus une séance en silence. Ni le jour, ni les
     * signatures, ni le patient ne sont regardés (KB-SEA-53, reproduit).
     */
    public void exigerSupprimable() {
        if (statut == StatutDeSeance.FACTUREE) {
            throw new SeanceRefusee(MotifDeRefus.SEANCE_FACTUREE_NON_SUPPRIMABLE,
                    "Une séance facturée ne peut pas être supprimée : annulez d'abord la facture");
        }
        if (facture != null) {
            throw new SeanceRefusee(MotifDeRefus.SEANCE_RATTACHEE_A_UNE_FACTURE,
                    "Une séance rattachée à une facture ne peut pas être supprimée : annulez d'abord la facture");
        }
    }

    /** Refuse (422) la séance facturée : à appeler avant de construire l'auteur, pour garder l'ordre des refus du legacy. */
    public void exigerNonFacturee() {
        if (statut == StatutDeSeance.FACTUREE) {
            throw new SeanceRefusee(MotifDeRefus.SEANCE_FACTUREE, "La seance facturee ne peut plus etre modifiee");
        }
    }

    /**
     * Qui a signé, déverrouillé, et où la séance a eu lieu (tous peuvent être nuls) : colonnes {@code signed_infirmier_by},
     * {@code signed_medecin_by}, {@code regularisation_deverrouillee_by} et place mémorisée.
     */
    public record Traces(String infirmier, String medecin, String deverrouilleePar, PlaceDeSeance place) {
        public static final Traces AUCUNE = new Traces(null, null, null, null);
    }

    private static <T> T exiger(T valeur, String message) {
        if (valeur == null) throw new SaisieInvalide(message);
        return valeur;
    }

    /**
     * Jalons de la vie de la séance après sa création (lecture seule pour SE1 ; écrits par le legacy jusqu'à SE2) : validation,
     * signature de l'infirmier, signature du médecin, déverrouillage pour régularisation. Tous peuvent être nuls.
     */
    public record Jalons(Instant valideeLe, Instant signeeParLInfirmierLe, Instant signeeParLeMedecinLe,
                         Instant deverrouilleePourRegularisationLe) {
        public static final Jalons AUCUN = new Jalons(null, null, null, null);
    }
}
