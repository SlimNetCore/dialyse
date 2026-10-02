package com.hemodialyse.backend.domain.absence.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Racine d'agrégat : absence d'un patient à une séance prévue. Une seule absence par patient et par jour.
 * <p>
 * Cycle de vie : {@code A_QUALIFIER} → {@code JUSTIFIEE | NON_JUSTIFIEE} (qualification par un motif) →
 * {@code RATTRAPEE} (séance de rattrapage : la perte est annulée) ; {@code ANNULEE} à tout moment avec un commentaire
 * (patient finalement présent, saisie erronée). Une absence annulée est définitive.
 * Seules les absences {@linkplain StatutAbsence#comptabilisee() comptabilisées} pèsent sur la valorisation.
 */
public final class AbsencePatient {

    /**
     * Une absence se déclare au plus {@value} jours après la séance manquée.
     */
    public static final int DELAI_DECLARATION_JOURS = 60;
    /**
     * Délai de qualification attendu : au-delà, l'absence est signalée « en retard ».
     */
    public static final int DELAI_QUALIFICATION_JOURS = 3;

    private final UUID id;
    private final UUID centerId;
    private final UUID patientId;
    private final LocalDate dateSeance;
    private final SourceAbsence source;
    private final ValeurAbsence valeur;
    private final UUID declareePar;
    private final Instant declareeLe;

    private StatutAbsence statut;
    private MotifAbsence motif;
    private String commentaire;
    private UUID qualifieePar;
    private Instant qualifieeLe;
    private LocalDate dateRattrapage;
    private UUID modifieePar;
    private Instant modifieeLe;

    @SuppressWarnings("java:S107")
    public AbsencePatient(UUID id, UUID centerId, UUID patientId, LocalDate dateSeance, SourceAbsence source,
                          ValeurAbsence valeur, UUID declareePar, Instant declareeLe, StatutAbsence statut,
                          MotifAbsence motif, String commentaire, UUID qualifieePar, Instant qualifieeLe,
                          LocalDate dateRattrapage, UUID modifieePar, Instant modifieeLe) {
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        if (dateSeance == null) throw new IllegalArgumentException("Date de séance requise");
        if (source == null) throw new IllegalArgumentException("Source requise");
        if (valeur == null) throw new IllegalArgumentException("Valeur requise");
        this.id = id;
        this.centerId = centerId;
        this.patientId = patientId;
        this.dateSeance = dateSeance;
        this.source = source;
        this.valeur = valeur;
        this.declareePar = declareePar;
        this.declareeLe = declareeLe;
        this.statut = statut;
        this.motif = motif;
        this.commentaire = commentaire;
        this.qualifieePar = qualifieePar;
        this.qualifieeLe = qualifieeLe;
        this.dateRattrapage = dateRattrapage;
        this.modifieePar = modifieePar;
        this.modifieeLe = modifieeLe;
    }

    /**
     * Absence détectée par le contrôle automatique : toujours « à qualifier ».
     */
    public static AbsencePatient detectee(UUID centerId, UUID patientId, LocalDate dateSeance, ValeurAbsence valeur,
                                          Instant maintenant) {
        return new AbsencePatient(UUID.randomUUID(), centerId, patientId, dateSeance, SourceAbsence.AUTOMATIQUE, valeur,
                null, maintenant, StatutAbsence.A_QUALIFIER, null, null, null, null, null, null, null);
    }

    /**
     * Absence déclarée par un utilisateur ; si un motif est fourni elle est qualifiée d'emblée. La date doit être
     * passée ou du jour, et pas plus ancienne que {@link #DELAI_DECLARATION_JOURS} jours.
     */
    public static AbsencePatient declaree(UUID centerId, UUID patientId, LocalDate dateSeance, MotifAbsence motif,
                                          String commentaire, ValeurAbsence valeur, UUID parUtilisateur,
                                          LocalDate aujourdhui, Instant maintenant) {
        if (dateSeance == null) throw new IllegalArgumentException("Date de séance requise");
        if (dateSeance.isAfter(aujourdhui)) {
            throw new BusinessException("ABSENCE_DATE_FUTURE", "Une absence ne peut pas être déclarée à une date future");
        }
        if (dateSeance.isBefore(aujourdhui.minusDays(DELAI_DECLARATION_JOURS))) {
            throw new BusinessException("ABSENCE_DELAI_DEPASSE",
                    "Une absence ne peut plus être déclarée au-delà de " + DELAI_DECLARATION_JOURS + " jours");
        }
        AbsencePatient a = new AbsencePatient(UUID.randomUUID(), centerId, patientId, dateSeance,
                SourceAbsence.DECLAREE, valeur, parUtilisateur, maintenant, StatutAbsence.A_QUALIFIER, null, null,
                null, null, null, null, null);
        if (motif != null) a.qualifier(motif, commentaire, parUtilisateur, maintenant);
        return a;
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Qualifie (ou requalifie) l'absence par un motif. Le motif {@code AUTRE} exige un commentaire ; une absence
     * rattrapée ou annulée ne se qualifie plus.
     */
    public void qualifier(MotifAbsence nouveauMotif, String nouveauCommentaire, UUID par, Instant maintenant) {
        exigerModifiable();
        if (nouveauMotif == null) throw new BusinessException("ABSENCE_MOTIF_REQUIS", "Le motif est obligatoire");
        String c = nettoyer(nouveauCommentaire);
        if (nouveauMotif.commentaireRequis() && c == null) {
            throw new BusinessException("ABSENCE_COMMENTAIRE_REQUIS", "Un commentaire est obligatoire pour ce motif");
        }
        boolean requalification = statut != StatutAbsence.A_QUALIFIER;
        if (requalification && c == null) {
            throw new BusinessException("ABSENCE_COMMENTAIRE_REQUIS",
                    "Un commentaire est obligatoire pour modifier une qualification");
        }
        this.motif = nouveauMotif;
        this.commentaire = c;
        this.statut = nouveauMotif.justifiante() ? StatutAbsence.JUSTIFIEE : StatutAbsence.NON_JUSTIFIEE;
        this.qualifieePar = par;
        this.qualifieeLe = maintenant;
        toucher(par, maintenant);
    }

    /**
     * Enregistre la séance de rattrapage : la perte n'est plus comptabilisée. Le rattrapage ne précède pas
     * l'absence et ne peut pas être dans le futur.
     */
    public void rattraper(LocalDate dateRattrapageSeance, UUID par, LocalDate aujourdhui, Instant maintenant) {
        exigerModifiable();
        if (dateRattrapageSeance == null) {
            throw new BusinessException("ABSENCE_RATTRAPAGE_DATE_REQUISE", "La date de rattrapage est obligatoire");
        }
        if (!dateRattrapageSeance.isAfter(dateSeance)) {
            throw new BusinessException("ABSENCE_RATTRAPAGE_ANTERIEUR",
                    "Le rattrapage doit avoir lieu après la séance manquée");
        }
        if (dateRattrapageSeance.isAfter(aujourdhui)) {
            throw new BusinessException("ABSENCE_DATE_FUTURE", "Le rattrapage ne peut pas être dans le futur");
        }
        this.statut = StatutAbsence.RATTRAPEE;
        this.dateRattrapage = dateRattrapageSeance;
        toucher(par, maintenant);
    }

    /**
     * Annule l'absence (patient finalement présent, déclaration erronée) : commentaire obligatoire, état définitif.
     */
    public void annuler(String motifAnnulation, UUID par, Instant maintenant) {
        if (statut == StatutAbsence.ANNULEE) {
            throw new BusinessException("ABSENCE_DEJA_ANNULEE", "L'absence est déjà annulée");
        }
        String c = nettoyer(motifAnnulation);
        if (c == null) {
            throw new BusinessException("ABSENCE_COMMENTAIRE_REQUIS", "Un commentaire est obligatoire pour annuler");
        }
        this.statut = StatutAbsence.ANNULEE;
        this.commentaire = c;
        toucher(par, maintenant);
    }

    /**
     * Vrai si l'absence attend sa qualification depuis plus de {@link #DELAI_QUALIFICATION_JOURS} jours.
     */
    public boolean qualificationEnRetard(LocalDate aujourdhui) {
        return statut == StatutAbsence.A_QUALIFIER && dateSeance.plusDays(DELAI_QUALIFICATION_JOURS).isBefore(aujourdhui);
    }

    private void exigerModifiable() {
        if (statut == StatutAbsence.ANNULEE || statut == StatutAbsence.RATTRAPEE) {
            throw new BusinessException("ABSENCE_NON_MODIFIABLE", "Cette absence n'est plus modifiable");
        }
    }

    private void toucher(UUID par, Instant maintenant) {
        this.modifieePar = par;
        this.modifieeLe = maintenant;
    }

    public UUID id() {
        return id;
    }

    public UUID centerId() {
        return centerId;
    }

    public UUID patientId() {
        return patientId;
    }

    public LocalDate dateSeance() {
        return dateSeance;
    }

    public SourceAbsence source() {
        return source;
    }

    public ValeurAbsence valeur() {
        return valeur;
    }

    public UUID declareePar() {
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

    public String commentaire() {
        return commentaire;
    }

    public UUID qualifieePar() {
        return qualifieePar;
    }

    public Instant qualifieeLe() {
        return qualifieeLe;
    }

    public LocalDate dateRattrapage() {
        return dateRattrapage;
    }

    public UUID modifieePar() {
        return modifieePar;
    }

    public Instant modifieeLe() {
        return modifieeLe;
    }
}
