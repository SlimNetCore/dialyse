package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bilan pré-greffe du patient receveur (table {@code bilans_pre_greffe}) et ses décisions de RCP : un seul par patient et par centre
 * (écart décidé KB-MED-90 : le legacy pouvait en créer deux). Racine d'agrégat.
 * <p>
 * Reproduits (à confirmer avec les métiers) : machine à états {@link StatutDuBilanPreGreffe} (KB-MED-92) ; la date de début n'est posée
 * qu'une fois, les dates d'inscription et de greffe sont réécrites à chaque appel du même statut ; les PUT immunologique et notes
 * remplacent tous leurs champs (champ absent effacé) ; aucune règle de valeur sur le PRA, le groupe sanguin ni le typage (KB-MED-93), hors
 * bornes de colonne (400 au lieu de 500 : groupe sanguin 5 caractères, typage 500, PRA 3 chiffres entiers et 2 décimales après arrondi).
 */
public final class BilanPreGreffe {
    private static final int GROUPE_SANGUIN_MAX = 5;
    private static final int TYPAGE_HLA_MAX = 500;
    private static final BigDecimal PRA_MAX_ARRONDI = new BigDecimal("999.99");

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final Instant creeLe;
    private final List<DecisionRcp> decisions;
    private StatutDuBilanPreGreffe statut;
    private LocalDate dateDebutBilan;
    private LocalDate dateInscriptionListeAttente;
    private LocalDate dateGreffe;
    private String groupeSanguinConfirme;
    private String typageHla;
    private BigDecimal praClasseI;
    private BigDecimal praClasseII;
    private String contreIndications;
    private String conclusionNephrologue;
    private Instant modifieLe;

    private BilanPreGreffe(UUID id, PatientId patient, CentreId centre, StatutDuBilanPreGreffe statut, LocalDate dateDebutBilan,
                           LocalDate dateInscriptionListeAttente, LocalDate dateGreffe, String groupeSanguinConfirme, String typageHla,
                           BigDecimal praClasseI, BigDecimal praClasseII, String contreIndications, String conclusionNephrologue,
                           List<DecisionRcp> decisions, Instant creeLe, Instant modifieLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.statut = statut;
        this.dateDebutBilan = dateDebutBilan;
        this.dateInscriptionListeAttente = dateInscriptionListeAttente;
        this.dateGreffe = dateGreffe;
        this.groupeSanguinConfirme = groupeSanguinConfirme;
        this.typageHla = typageHla;
        this.praClasseI = praClasseI;
        this.praClasseII = praClasseII;
        this.contreIndications = contreIndications;
        this.conclusionNephrologue = conclusionNephrologue;
        this.decisions = new ArrayList<>(decisions);
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    /** Ouvre le bilan d'un patient : statut {@code NON_DEBUTE}, rien d'autre de renseigné. */
    public static BilanPreGreffe ouvrir(UUID id, PatientId patient, CentreId centre, Instant maintenant) {
        return new BilanPreGreffe(id, patient, centre, StatutDuBilanPreGreffe.NON_DEBUTE, null, null, null, null, null, null, null, null,
                null, List.of(), maintenant, maintenant);
    }

    public static BilanPreGreffe reconstituer(UUID id, PatientId patient, CentreId centre, StatutDuBilanPreGreffe statut,
                                              LocalDate dateDebutBilan, LocalDate dateInscriptionListeAttente, LocalDate dateGreffe,
                                              String groupeSanguinConfirme, String typageHla, BigDecimal praClasseI, BigDecimal praClasseII,
                                              String contreIndications, String conclusionNephrologue, List<DecisionRcp> decisions,
                                              Instant creeLe, Instant modifieLe) {
        return new BilanPreGreffe(id, patient, centre, statut, dateDebutBilan, dateInscriptionListeAttente, dateGreffe,
                groupeSanguinConfirme, typageHla, praClasseI, praClasseII, contreIndications, conclusionNephrologue, decisions, creeLe,
                modifieLe);
    }

    /** Change le statut selon la machine à états ; les dates de début, d'inscription et de greffe sont celles de {@code aujourdhui}. */
    public void changerLeStatut(StatutDuBilanPreGreffe nouveau, LocalDate aujourdhui, Instant maintenant) {
        if (nouveau == null) throw new RegleDeGestionViolee("BILAN_GREFFE_STATUT_REQUIS", "Le statut est obligatoire");
        if (!statut.autorise(nouveau)) {
            throw new RegleDeGestionViolee("BILAN_GREFFE_TRANSITION_INVALIDE", "Transition de statut invalide : " + statut + " → " + nouveau);
        }
        this.statut = nouveau;
        if (nouveau == StatutDuBilanPreGreffe.BILAN_EN_COURS && dateDebutBilan == null) this.dateDebutBilan = aujourdhui;
        if (nouveau == StatutDuBilanPreGreffe.INSCRIT_LISTE_ATTENTE) this.dateInscriptionListeAttente = aujourdhui;
        if (nouveau == StatutDuBilanPreGreffe.GREFFE_REALISEE) this.dateGreffe = aujourdhui;
        this.modifieLe = maintenant;
    }

    /** Remplace les quatre champs du bilan immunologique (un champ absent est effacé). */
    public void mettreAJourLeBilanImmunologique(String groupeSanguinConfirme, String typageHla, BigDecimal praClasseI, BigDecimal praClasseII,
                                                Instant maintenant) {
        if (groupeSanguinConfirme != null && groupeSanguinConfirme.length() > GROUPE_SANGUIN_MAX) {
            throw new SaisieInvalide("Le groupe sanguin dépasse " + GROUPE_SANGUIN_MAX + " caractères");
        }
        if (typageHla != null && typageHla.length() > TYPAGE_HLA_MAX) {
            throw new SaisieInvalide("Le typage HLA dépasse " + TYPAGE_HLA_MAX + " caractères");
        }
        verifierLePra(praClasseI);
        verifierLePra(praClasseII);
        this.groupeSanguinConfirme = groupeSanguinConfirme;
        this.typageHla = typageHla;
        this.praClasseI = praClasseI;
        this.praClasseII = praClasseII;
        this.modifieLe = maintenant;
    }

    private static void verifierLePra(BigDecimal pra) {
        if (pra == null) return;
        if (pra.setScale(2, RoundingMode.HALF_UP).abs().compareTo(PRA_MAX_ARRONDI) > 0) {
            throw new SaisieInvalide("Un PRA dépasse 3 chiffres entiers et 2 décimales");
        }
    }

    /** Remplace les deux textes (sans limite de longueur ; un texte absent est effacé). */
    public void mettreAJourLesNotes(String contreIndications, String conclusionNephrologue, Instant maintenant) {
        this.contreIndications = contreIndications;
        this.conclusionNephrologue = conclusionNephrologue;
        this.modifieLe = maintenant;
    }

    public DecisionRcp ajouterUneDecision(UUID id, LocalDate dateReunion, AvisRcp avis, String compteRendu, LocalDate prochaineDateRevue,
                                          LocalDate aujourdhui, Instant maintenant) {
        DecisionRcp decision = DecisionRcp.enregistrer(id, dateReunion, avis, compteRendu, prochaineDateRevue, aujourdhui);
        this.decisions.add(decision);
        this.modifieLe = maintenant;
        return decision;
    }

    public UUID id() {
        return id;
    }

    public PatientId patient() {
        return patient;
    }

    public CentreId centre() {
        return centre;
    }

    public StatutDuBilanPreGreffe statut() {
        return statut;
    }

    public LocalDate dateDebutBilan() {
        return dateDebutBilan;
    }

    public LocalDate dateInscriptionListeAttente() {
        return dateInscriptionListeAttente;
    }

    public LocalDate dateGreffe() {
        return dateGreffe;
    }

    public String groupeSanguinConfirme() {
        return groupeSanguinConfirme;
    }

    public String typageHla() {
        return typageHla;
    }

    public BigDecimal praClasseI() {
        return praClasseI;
    }

    public BigDecimal praClasseII() {
        return praClasseII;
    }

    public String contreIndications() {
        return contreIndications;
    }

    public String conclusionNephrologue() {
        return conclusionNephrologue;
    }

    /** Décisions de la plus récente réunion à la plus ancienne (ordre de lecture ; ajout en fin de liste). */
    public List<DecisionRcp> decisions() {
        return List.copyOf(decisions);
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
