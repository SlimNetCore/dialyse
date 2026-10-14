package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Équipement du parc d'un centre (racine d'agrégat) : générateur de dialyse, station de traitement d'eau, etc. Garantit
 * le cycle de vie : la réforme est terminale (R1), la mise hors service est refusée si déjà hors service (R2), la remise
 * en service ne part que de {@code HORS_SERVICE} (R3), une intervention ne fixe jamais {@code REFORME} ni {@code DESACTIF}
 * (R4), code, type et date d'installation sont immuables (R5), tout changement de statut est historisé (R8). L'unicité du
 * code par centre (R7) relève de {@link UniciteDuCodeEquipement}. L'heure arrive par paramètre.
 * <p>
 * Comportements du legacy reproduits (ADR 0019) : la correction est un remplacement total (KB-GMAO-36) ; les observations
 * s'accumulent, séparées par {@code "; "} (changements de statut) ou un saut de ligne (observations) ; le motif de
 * l'historique est le cumul des observations pour les commandes de l'administrateur (KB-GMAO-41) ; les observations et la
 * correction restent possibles sur un équipement réformé (KB-GMAO-36).
 */
public final class Equipement {

    private final EquipementId id;
    private final CentreId centreId;
    private final CodeEquipement code;
    private final TypeEquipement type;
    private final Instant dateInstallation;
    private final Instant creeLe;
    private final UtilisateurId creePar;

    private String designation;
    private String fabricant;
    private String modele;
    private String numeroSerie;
    private String localisation;
    private SalleId salleId;
    private PrixAcquisition prixAcquisition;
    private StatutEquipement statut;
    private String observations;
    private Instant modifieLe;
    private UtilisateurId modifiePar;

    private final List<EvenementDEquipement> evenements = new ArrayList<>();
    private final List<ChangementDeStatut> changements = new ArrayList<>();

    private Equipement(EquipementId id, CentreId centreId, CodeEquipement code, TypeEquipement type,
                       Instant dateInstallation, Instant creeLe, UtilisateurId creePar, String designation,
                       String fabricant, String modele, String numeroSerie, String localisation, SalleId salleId,
                       PrixAcquisition prixAcquisition, StatutEquipement statut, String observations, Instant modifieLe,
                       UtilisateurId modifiePar) {
        this.id = id;
        this.centreId = centreId;
        this.code = code;
        this.type = type;
        this.dateInstallation = dateInstallation;
        this.creeLe = creeLe;
        this.creePar = creePar;
        this.designation = designation;
        this.fabricant = fabricant;
        this.modele = modele;
        this.numeroSerie = numeroSerie;
        this.localisation = localisation;
        this.salleId = salleId;
        this.prixAcquisition = prixAcquisition;
        this.statut = statut;
        this.observations = observations;
        this.modifieLe = modifieLe;
        this.modifiePar = modifiePar;
    }

    /** Enregistre un équipement en service. Le code n'est ni rogné ni normalisé (KB-GMAO-33). */
    public static Equipement enregistrer(EquipementId id, CentreId centreId, CodeEquipement code, String designation,
                                         TypeEquipement type, String fabricant, String modele, String numeroSerie,
                                         Instant dateInstallation, String localisation, SalleId salleId,
                                         PrixAcquisition prixAcquisition, UtilisateurId par, Instant maintenant) {
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (code == null) throw new IllegalArgumentException("Code requis");
        exigerDesignation(designation);
        if (type == null) throw new IllegalArgumentException("Type d'équipement requis");
        if (dateInstallation == null) throw new IllegalArgumentException("Date d'installation requise");

        Equipement equipement = new Equipement(id, centreId, code, type, dateInstallation, maintenant, par, designation,
                fabricant, modele, numeroSerie, localisation, salleId, prixAcquisition, StatutEquipement.EN_SERVICE,
                null, null, null);
        equipement.changements.add(new ChangementDeStatut(null, StatutEquipement.EN_SERVICE, null, par, maintenant));
        equipement.evenements.add(new EquipementEnregistre(id, centreId, type, code, salleId, par, maintenant));
        return equipement;
    }

    /** Reconstitution depuis la persistance (adapters seulement) : aucun événement, aucune règle rejouée. */
    public static Equipement reconstituer(EquipementId id, CentreId centreId, CodeEquipement code, String designation,
                                          TypeEquipement type, String fabricant, String modele, String numeroSerie,
                                          Instant dateInstallation, StatutEquipement statut, String localisation,
                                          String observations, SalleId salleId, PrixAcquisition prixAcquisition,
                                          Instant creeLe, Instant modifieLe, UtilisateurId creePar,
                                          UtilisateurId modifiePar) {
        return new Equipement(id, centreId, code, type, dateInstallation, creeLe, creePar, designation, fabricant, modele,
                numeroSerie, localisation, salleId, prixAcquisition, statut, observations, modifieLe, modifiePar);
    }

    /**
     * Remplace désignation, fabricant, modèle, numéro de série, localisation, salle et prix (remplacement total : les
     * valeurs omises sont effacées, salle comprise, KB-GMAO-36). Code, type et date d'installation ne changent jamais (R5) ;
     * le statut non plus : aucun historique. Possible sur un équipement réformé.
     */
    public void corrigerIdentification(String designation, String fabricant, String modele, String numeroSerie,
                                       String localisation, SalleId salleId, PrixAcquisition prixAcquisition,
                                       UtilisateurId par, Instant maintenant) {
        exigerDesignation(designation);
        this.designation = designation;
        this.fabricant = fabricant;
        this.modele = modele;
        this.numeroSerie = numeroSerie;
        this.localisation = localisation;
        this.salleId = salleId;
        this.prixAcquisition = prixAcquisition;
        toucher(par, maintenant);
        evenements.add(new IdentificationEquipementCorrigee(id, centreId, type, salleId, par, maintenant));
    }

    /** Met l'équipement hors service (R1, R2). Les observations gardent la raison, préfixée « Hors service: ». */
    public void mettreHorsService(String raison, UtilisateurId par, Instant maintenant) {
        if (statut == StatutEquipement.REFORME) throw EquipementReformeImmuable.pourChangerDeStatut();
        if (statut == StatutEquipement.HORS_SERVICE) throw new EquipementDejaHorsService();
        if (raison == null || raison.isBlank()) throw new IllegalArgumentException("Raison requise");
        StatutEquipement precedent = statut;
        statut = StatutEquipement.HORS_SERVICE;
        observations = cumuler("; ", "Hors service: " + raison);
        toucher(par, maintenant);
        changements.add(new ChangementDeStatut(precedent, statut, observations, par, maintenant));
        evenements.add(new EquipementMisHorsService(id, centreId, type, code, precedent, statut,
                CauseDIndisponibilite.MISE_HORS_SERVICE, par, maintenant));
    }

    /** Remet en service un équipement hors service, et lui seul (R1, R3). Les observations ne changent pas. */
    public void remettreEnService(UtilisateurId par, Instant maintenant) {
        if (statut == StatutEquipement.REFORME) throw EquipementReformeImmuable.pourRemettreEnService();
        if (statut != StatutEquipement.HORS_SERVICE) throw new RemiseEnServiceImpossible();
        StatutEquipement precedent = statut;
        statut = StatutEquipement.EN_SERVICE;
        toucher(par, maintenant);
        changements.add(new ChangementDeStatut(precedent, statut, observations, par, maintenant));
        evenements.add(new EquipementRemisEnService(id, centreId, type, code, precedent, par, maintenant));
    }

    /**
     * Applique l'état relevé par une intervention (au démarrage et à la clôture). {@code REFORME} et {@code DESACTIF} ne se
     * fixent jamais ainsi (R4) ; un équipement réformé ne change plus (R1). Idempotent : aucun effet si l'équipement est
     * déjà dans cet état. Reproduit le legacy (KB-GMAO-40) : toute sortie d'un autre statut est permise, hors service compris.
     * Le motif de l'intervention est celui de l'historique.
     *
     * @return vrai si le statut a changé
     */
    public boolean constaterEtat(StatutEquipement etat, String motif, UtilisateurId par, Instant maintenant) {
        if (etat == null) throw new IllegalArgumentException("Nouvel état requis");
        if (statut == StatutEquipement.REFORME) throw EquipementReformeImmuable.pourChangerDeStatut();
        if (etat == StatutEquipement.REFORME || etat == StatutEquipement.DESACTIF) {
            throw new EtatNonFixableParIntervention();
        }
        if (etat == statut) return false;
        StatutEquipement precedent = statut;
        statut = etat;
        observations = cumuler("; ", motif);
        toucher(par, maintenant);
        changements.add(new ChangementDeStatut(precedent, statut, motif, par, maintenant));
        evenements.add(etat == StatutEquipement.EN_SERVICE
                ? new EquipementRemisEnService(id, centreId, type, code, precedent, par, maintenant)
                : new EquipementMisHorsService(id, centreId, type, code, precedent, statut,
                        CauseDIndisponibilite.INTERVENTION, par, maintenant));
        return true;
    }

    /** Réforme l'équipement : fin de vie, état terminal (R1). Depuis n'importe quel autre statut. */
    public void reformer(String motif, UtilisateurId par, Instant maintenant) {
        if (statut == StatutEquipement.REFORME) throw new EquipementDejaReforme();
        if (motif == null || motif.isBlank()) throw new IllegalArgumentException("Motif de réforme requis");
        StatutEquipement precedent = statut;
        statut = StatutEquipement.REFORME;
        observations = cumuler("; ", "Réformé: " + motif);
        toucher(par, maintenant);
        changements.add(new ChangementDeStatut(precedent, statut, observations, par, maintenant));
        evenements.add(new EquipementReforme(id, centreId, type, code, precedent, par, maintenant));
    }

    /** Consigne une observation horodatée, à la suite des précédentes (saut de ligne). Possible sur un équipement réformé. */
    public void consignerObservation(String observation, UtilisateurId par, Instant maintenant) {
        if (observation == null || observation.isBlank()) throw new IllegalArgumentException("Observation requise");
        observations = cumuler("\n", maintenant + " - " + observation);
        toucher(par, maintenant);
    }

    /** Retire et retourne les événements levés depuis la dernière publication. */
    public List<EvenementDEquipement> tirerEvenements() {
        List<EvenementDEquipement> tires = List.copyOf(evenements);
        evenements.clear();
        return tires;
    }

    /** Retire et retourne les changements de statut à inscrire à l'historique (écrits par le repository avec l'équipement). */
    public List<ChangementDeStatut> tirerChangementsDeStatut() {
        List<ChangementDeStatut> tires = List.copyOf(changements);
        changements.clear();
        return tires;
    }

    private String cumuler(String separateur, String ajout) {
        return (observations != null ? observations + separateur : "") + ajout;
    }

    private void toucher(UtilisateurId par, Instant maintenant) {
        modifieLe = maintenant;
        modifiePar = par;
    }

    private static void exigerDesignation(String designation) {
        if (designation == null || designation.isBlank()) throw new IllegalArgumentException("Désignation requise");
    }

    public EquipementId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public CodeEquipement code() {
        return code;
    }

    public String designation() {
        return designation;
    }

    public TypeEquipement type() {
        return type;
    }

    public String fabricant() {
        return fabricant;
    }

    public String modele() {
        return modele;
    }

    public String numeroSerie() {
        return numeroSerie;
    }

    public Instant dateInstallation() {
        return dateInstallation;
    }

    public StatutEquipement statut() {
        return statut;
    }

    public String localisation() {
        return localisation;
    }

    public String observations() {
        return observations;
    }

    /** Salle d'affectation, ou {@code null}. */
    public SalleId salleId() {
        return salleId;
    }

    /** Prix d'acquisition, ou {@code null}. */
    public PrixAcquisition prixAcquisition() {
        return prixAcquisition;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }

    public UtilisateurId creePar() {
        return creePar;
    }

    public UtilisateurId modifiePar() {
        return modifiePar;
    }
}
