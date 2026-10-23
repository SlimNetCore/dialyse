package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceAnnulee;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceDeclaree;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceDetectee;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceReconciliee;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceQualifiee;
import com.hemodialyse.backend.contexts.absencespatients.domain.EvenementDAbsence.AbsenceRattrapee;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Racine d'agrégat : l'absence d'un patient à une séance prévue, suivie jusqu'à son issue. Une seule absence par patient et
 * par jour, même annulée (contrainte de la table). Cycle : {@code A_QUALIFIER} puis {@code JUSTIFIEE} ou
 * {@code NON_JUSTIFIEE} (motif), puis {@code RATTRAPEE} (séance de rattrapage) ; {@code ANNULEE} tant qu'elle n'est pas
 * rattrapée, de façon définitive. Une absence rattrapée ou annulée n'est plus modifiable (RG-ABS-003).
 * <p>
 * La valorisation est figée à la création : aucune commande ne l'écrit ensuite. Les contrôles d'une écriture sur une absence
 * existante suivent toujours l'ordre existence (use case), droit de correction, état, date ouverte, valeurs.
 */
public final class AbsencePatient {

    /** Une absence se déclare au plus {@value} jours après la séance manquée (RG-ABS-010). */
    public static final int DELAI_DE_DECLARATION_EN_JOURS = 60;

    /** Commentaire de l'annulation par réconciliation (valeur du legacy, conservée). */
    public static final String COMMENTAIRE_DE_RECONCILIATION = "Séance réalisée à cette date";

    private final AbsenceId id;
    private final CentreId centre;
    private final PatientId patient;
    private final LocalDate dateDeSeance;
    private final SourceAbsence source;
    private final Valorisation valorisation;
    private final UtilisateurId declareePar;
    private final Instant declareeLe;

    private StatutAbsence statut;
    private MotifAbsence motif;
    private Commentaire commentaire;
    private UtilisateurId qualifieePar;
    private Instant qualifieeLe;
    private LocalDate dateDeRattrapage;
    private UtilisateurId modifieePar;
    private Instant modifieeLe;
    private final List<EvenementDAbsence> evenements = new ArrayList<>();

    @SuppressWarnings("java:S107")
    private AbsencePatient(AbsenceId id, CentreId centre, PatientId patient, LocalDate dateDeSeance, SourceAbsence source,
                           Valorisation valorisation, UtilisateurId declareePar, Instant declareeLe, StatutAbsence statut,
                           MotifAbsence motif, Commentaire commentaire, UtilisateurId qualifieePar, Instant qualifieeLe,
                           LocalDate dateDeRattrapage, UtilisateurId modifieePar, Instant modifieeLe) {
        if (id == null || centre == null || patient == null || dateDeSeance == null || source == null
                || valorisation == null || statut == null) {
            throw new IllegalArgumentException(
                    "Une absence a une identité, un centre, un patient, une date, une source, une valeur et un statut");
        }
        this.id = id;
        this.centre = centre;
        this.patient = patient;
        this.dateDeSeance = dateDeSeance;
        this.source = source;
        this.valorisation = valorisation;
        this.declareePar = declareePar;
        this.declareeLe = declareeLe;
        this.statut = statut;
        this.motif = motif;
        this.commentaire = commentaire;
        this.qualifieePar = qualifieePar;
        this.qualifieeLe = qualifieeLe;
        this.dateDeRattrapage = dateDeRattrapage;
        this.modifieePar = modifieePar;
        this.modifieeLe = modifieeLe;
    }

    /**
     * Relit une absence enregistrée (adaptateur de persistance) : aucune règle de création n'est rejouée.
     */
    @SuppressWarnings("java:S107")
    public static AbsencePatient reconstituer(AbsenceId id, CentreId centre, PatientId patient, LocalDate dateDeSeance,
                                              SourceAbsence source, Valorisation valorisation, UtilisateurId declareePar,
                                              Instant declareeLe, StatutAbsence statut, MotifAbsence motif,
                                              String commentaire, UtilisateurId qualifieePar, Instant qualifieeLe,
                                              LocalDate dateDeRattrapage, UtilisateurId modifieePar, Instant modifieeLe) {
        return new AbsencePatient(id, centre, patient, dateDeSeance, source, valorisation, declareePar, declareeLe, statut,
                motif, commentaire == null || commentaire.isBlank() ? null : new Commentaire(commentaire), qualifieePar, qualifieeLe,
                dateDeRattrapage, modifieePar, modifieeLe);
    }

    /**
     * Déclare l'absence d'un patient : « à qualifier », ou qualifiée d'emblée si un motif est fourni (RG-ABS-013). La date
     * n'est ni future ni antérieure de plus de {@value #DELAI_DE_DECLARATION_EN_JOURS} jours (RG-ABS-010). Un commentaire
     * fourni sans motif est conservé (KB-ABS-12 corrigé).
     *
     * @param ouverte preuve que la période du patient n'est pas facturée
     */
    @SuppressWarnings("java:S107")
    public static AbsencePatient declarer(AbsenceId id, DateOuverte ouverte, MotifAbsence motif, String commentaire,
                                          Valorisation valorisation, UtilisateurId auteur, LocalDate aujourdhui,
                                          Instant maintenant) {
        LocalDate date = ouverte.jour();
        if (date.isAfter(aujourdhui)) {
            throw new RegleDAbsenceViolee("ABSENCE_DATE_FUTURE", "Une absence ne peut pas être déclarée à une date future");
        }
        if (date.isBefore(aujourdhui.minusDays(DELAI_DE_DECLARATION_EN_JOURS))) {
            throw new RegleDAbsenceViolee("ABSENCE_DELAI_DEPASSE",
                    "Une absence ne peut plus être déclarée au-delà de " + DELAI_DE_DECLARATION_EN_JOURS + " jours");
        }
        Optional<Commentaire> texte = Commentaire.de(commentaire);
        AbsencePatient absence = new AbsencePatient(id, ouverte.centre(), ouverte.patient(), date, SourceAbsence.DECLAREE,
                valorisation, auteur, maintenant, StatutAbsence.A_QUALIFIER, null, texte.orElse(null), null, null, null,
                null, null);
        absence.evenements.add(new AbsenceDeclaree(id, ouverte.centre(), ouverte.patient(), date, auteur, maintenant));
        if (motif != null) {
            exigerLeCommentaireDuMotif(motif, texte);
            absence.appliquerLaQualification(motif, texte, auteur, maintenant);
        }
        return absence;
    }

    /**
     * Détecte l'absence d'un patient attendu sans séance réalisée (contrôle de nuit, RG-ABS-040) : toujours « à qualifier », source
     * automatique, sans auteur.
     *
     * @param ouverte preuve que la période du patient n'est pas facturée et que le mois d'absences est ouvert
     */
    public static AbsencePatient detecter(AbsenceId id, DateOuverte ouverte, Valorisation valorisation, Instant maintenant) {
        AbsencePatient absence = new AbsencePatient(id, ouverte.centre(), ouverte.patient(), ouverte.jour(),
                SourceAbsence.AUTOMATIQUE, valorisation, null, maintenant, StatutAbsence.A_QUALIFIER, null, null, null, null,
                null, null, null);
        absence.evenements.add(new AbsenceDetectee(id, ouverte.centre(), ouverte.patient(), ouverte.jour(), null, maintenant));
        return absence;
    }

    /**
     * La séance finalement réalisée annule seulement une absence détectée par le contrôle de nuit et encore « à qualifier » : ni une
     * absence déclarée par un utilisateur (KB-ABS-22 corrigé), ni une absence qualifiée, ni une absence dont la période est facturée
     * (RG-ABS-024, KB-ABS-22 corrigé).
     */
    public boolean reconciliable(PeriodesFacturees periodes) {
        return source == SourceAbsence.AUTOMATIQUE && statut == StatutAbsence.A_QUALIFIER
                && !periodes.estFacturee(centre, patient, dateDeSeance);
    }

    /**
     * Annule l'absence parce que la séance a été réalisée à cette date (saisie tardive) : commentaire fixe, sans auteur.
     *
     * @throws RegleDAbsenceViolee {@code ABSENCE_NON_RECONCILIABLE} si {@link #reconciliable} est faux
     */
    public void reconcilier(PeriodesFacturees periodes, Instant maintenant) {
        if (!reconciliable(periodes)) {
            throw new RegleDAbsenceViolee("ABSENCE_NON_RECONCILIABLE",
                    "Seule une absence détectée, encore à qualifier et hors période facturée se réconcilie");
        }
        this.statut = StatutAbsence.ANNULEE;
        this.commentaire = new Commentaire(COMMENTAIRE_DE_RECONCILIATION);
        toucher(null, maintenant);
        evenements.add(new AbsenceReconciliee(id, centre, patient, dateDeSeance, null, maintenant));
    }

    /**
     * Qualifie, ou requalifie, l'absence par un motif (RG-ABS-004, 005, 020). Requalifier une absence déjà qualifiée exige le
     * droit de correction et un commentaire ; le commentaire précédent est remplacé (KB-ABS-14, reproduit).
     */
    public void qualifier(MotifAbsence nouveauMotif, String nouveauCommentaire, UtilisateurId auteur, DroitDeCorrection droit,
                          PeriodesFacturees periodes, Instant maintenant) {
        exigerLeDroitDeCorrectionSiQualifiee(droit);
        exigerModifiable();
        DateOuverte.exiger(periodes, centre, patient, dateDeSeance);
        if (nouveauMotif == null) {
            throw new RegleDAbsenceViolee("ABSENCE_MOTIF_REQUIS", "Le motif est obligatoire");
        }
        Optional<Commentaire> texte = Commentaire.de(nouveauCommentaire);
        exigerLeCommentaireDuMotif(nouveauMotif, texte);
        if (statut != StatutAbsence.A_QUALIFIER && texte.isEmpty()) {
            throw new RegleDAbsenceViolee("ABSENCE_COMMENTAIRE_REQUIS",
                    "Un commentaire est obligatoire pour modifier une qualification");
        }
        appliquerLaQualification(nouveauMotif, texte, auteur, maintenant);
    }

    /**
     * Enregistre la séance de rattrapage : la perte n'est plus comptabilisée (RG-ABS-021). Réservé au droit de correction, quelle
     * que soit la qualification (KB-ABS-13 corrigé). La date suit la séance manquée, n'est pas future, tombe sur une séance
     * réalisée du patient et ne sert pas déjà à une autre absence (une séance ne rattrape qu'une absence).
     */
    public void rattraper(LocalDate dateDeRattrapageDemandee, UtilisateurId auteur, DroitDeCorrection droit,
                          PeriodesFacturees periodes, FaitsDuRattrapage faits, LocalDate aujourdhui, Instant maintenant) {
        exigerLeDroitDeCorrection(droit);
        exigerModifiable();
        DateOuverte.exiger(periodes, centre, patient, dateDeSeance);
        if (dateDeRattrapageDemandee == null) {
            throw new RegleDAbsenceViolee("ABSENCE_RATTRAPAGE_DATE_REQUISE", "La date de rattrapage est obligatoire");
        }
        if (!dateDeRattrapageDemandee.isAfter(dateDeSeance)) {
            throw new RegleDAbsenceViolee("ABSENCE_RATTRAPAGE_ANTERIEUR",
                    "Le rattrapage doit avoir lieu après la séance manquée");
        }
        if (dateDeRattrapageDemandee.isAfter(aujourdhui)) {
            throw new RegleDAbsenceViolee("ABSENCE_DATE_FUTURE", "Le rattrapage ne peut pas être dans le futur");
        }
        if (!faits.seanceRealisee(dateDeRattrapageDemandee)) {
            throw new RegleDAbsenceViolee("ABSENCE_RATTRAPAGE_SANS_SEANCE",
                    "Aucune séance réalisée pour ce patient à la date de rattrapage");
        }
        if (faits.dejaUtilisee(dateDeRattrapageDemandee)) {
            throw new RegleDAbsenceViolee("ABSENCE_RATTRAPAGE_DEJA_UTILISEE",
                    "Cette séance rattrape déjà une autre absence de ce patient");
        }
        this.statut = StatutAbsence.RATTRAPEE;
        this.dateDeRattrapage = dateDeRattrapageDemandee;
        toucher(auteur, maintenant);
        evenements.add(new AbsenceRattrapee(id, centre, patient, dateDeSeance, auteur, maintenant, dateDeRattrapageDemandee));
    }

    /**
     * Annule l'absence (patient finalement présent, déclaration erronée) : commentaire obligatoire, état définitif. Annuler
     * une absence déjà qualifiée exige le droit de correction ; une absence rattrapée ne s'annule plus (KB-ABS-10 corrigé :
     * sa date de rattrapage reste donc celle de la séance qui l'a rattrapée) ; l'absence créée par l'exclusion d'une séance ne s'annule
     * pas : seule la réinclusion de la séance la retire (KB-ABS-20).
     */
    public void annuler(String motifDAnnulation, UtilisateurId auteur, DroitDeCorrection droit, boolean issueDUneExclusion,
                        PeriodesFacturees periodes, Instant maintenant) {
        exigerLeDroitDeCorrectionSiQualifiee(droit);
        if (statut == StatutAbsence.ANNULEE) {
            throw new RegleDAbsenceViolee("ABSENCE_DEJA_ANNULEE", "L'absence est déjà annulée");
        }
        exigerModifiable();
        if (issueDUneExclusion) {
            throw new RegleDAbsenceViolee("ABSENCE_D_EXCLUSION",
                    "Cette absence vient de l'exclusion d'une séance de la facturation : réincluez la séance pour la retirer");
        }
        DateOuverte.exiger(periodes, centre, patient, dateDeSeance);
        Commentaire texte = Commentaire.de(motifDAnnulation).orElseThrow(() ->
                new RegleDAbsenceViolee("ABSENCE_COMMENTAIRE_REQUIS", "Un commentaire est obligatoire pour annuler"));
        this.statut = StatutAbsence.ANNULEE;
        this.commentaire = texte;
        toucher(auteur, maintenant);
        evenements.add(new AbsenceAnnulee(id, centre, patient, dateDeSeance, auteur, maintenant));
    }

    /**
     * Retrait par la réinclusion de la séance exclue (ADR 0031) : seule la réinclusion retire l'absence d'une exclusion, quel que soit
     * son état, sauf une absence déjà annulée (cas hérités d'avant A2, à traiter à la main, jamais supprimés par l'application).
     *
     * @throws RegleDAbsenceViolee {@code ABSENCE_D_EXCLUSION_MODIFIEE} si l'absence est annulée
     */
    public void exigerRetirableParLaReinclusion() {
        if (statut == StatutAbsence.ANNULEE) {
            throw new RegleDAbsenceViolee("ABSENCE_D_EXCLUSION_MODIFIEE",
                    "L'absence de cette exclusion a été annulée : elle n'est pas retirée par la réinclusion, à traiter avec l'administrateur");
        }
    }

    /**
     * Vue de lecture de l'absence, au format du suivi.
     */
    public AbsenceSuivie vue() {
        return new AbsenceSuivie(id, patient, dateDeSeance, source, statut, motif, commentaire(),
                valorisation.libelleDuForfait(), valorisation.montantTtc(), valorisation.tauxDeTva(),
                valorisation.montantHt(), declareeLe, qualifieeLe, dateDeRattrapage);
    }

    /**
     * Événements levés depuis la dernière extraction, dans l'ordre ; la liste est vidée.
     */
    public List<EvenementDAbsence> tirerLesEvenements() {
        List<EvenementDAbsence> tires = List.copyOf(evenements);
        evenements.clear();
        return tires;
    }

    private void appliquerLaQualification(MotifAbsence nouveauMotif, Optional<Commentaire> texte, UtilisateurId auteur,
                                          Instant maintenant) {
        this.motif = nouveauMotif;
        // une qualification sans commentaire garde celui déjà présent (KB-ABS-12 et 14 alignés : rien ne s'efface sans bruit)
        this.commentaire = texte.orElse(this.commentaire);
        this.statut = nouveauMotif.justifiante() ? StatutAbsence.JUSTIFIEE : StatutAbsence.NON_JUSTIFIEE;
        this.qualifieePar = auteur;
        this.qualifieeLe = maintenant;
        toucher(auteur, maintenant);
        evenements.add(new AbsenceQualifiee(id, centre, patient, dateDeSeance, auteur, maintenant, nouveauMotif));
    }

    private static void exigerLeCommentaireDuMotif(MotifAbsence motif, Optional<Commentaire> texte) {
        if (motif.commentaireRequis() && texte.isEmpty()) {
            throw new RegleDAbsenceViolee("ABSENCE_COMMENTAIRE_REQUIS", "Un commentaire est obligatoire pour ce motif");
        }
    }

    private void exigerLeDroitDeCorrection(DroitDeCorrection droit) {
        if (!droit.accorde()) {
            throw new RegleDAbsenceViolee("ABSENCE_CORRECTION_INTERDITE",
                    "Seuls un administrateur ou un médecin peuvent rattraper une absence");
        }
    }

    private void exigerLeDroitDeCorrectionSiQualifiee(DroitDeCorrection droit) {
        if (statut != StatutAbsence.A_QUALIFIER && !droit.accorde()) {
            throw new RegleDAbsenceViolee("ABSENCE_CORRECTION_INTERDITE",
                    "Seuls un administrateur ou un médecin peuvent corriger une absence déjà qualifiée");
        }
    }

    private void exigerModifiable() {
        if (!statut.modifiable()) {
            throw new AbsenceNonModifiable();
        }
    }

    private void toucher(UtilisateurId auteur, Instant maintenant) {
        this.modifieePar = auteur;
        this.modifieeLe = maintenant;
    }

    public AbsenceId id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public PatientId patient() {
        return patient;
    }

    public LocalDate dateDeSeance() {
        return dateDeSeance;
    }

    public SourceAbsence source() {
        return source;
    }

    public Valorisation valorisation() {
        return valorisation;
    }

    public UtilisateurId declareePar() {
        return declareePar;
    }

    public Instant declareeLe() {
        return declareeLe;
    }

    public StatutAbsence statut() {
        return statut;
    }

    public MotifAbsence motif() {
        return motif;
    }

    /** Nul si l'absence n'a pas de commentaire. */
    public String commentaire() {
        return commentaire == null ? null : commentaire.texte();
    }

    public UtilisateurId qualifieePar() {
        return qualifieePar;
    }

    public Instant qualifieeLe() {
        return qualifieeLe;
    }

    public LocalDate dateDeRattrapage() {
        return dateDeRattrapage;
    }

    public UtilisateurId modifieePar() {
        return modifieePar;
    }

    public Instant modifieeLe() {
        return modifieeLe;
    }
}
