package com.hemodialyse.backend.domain.absence.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Racine d'agrégat : absence d'un patient à une séance prévue. Une seule absence par patient et par jour.
 * <p>
 * Reste au legacy pour la détection de nuit et la réconciliation (slice A3) : une absence détectée est « à qualifier » et
 * s'annule quand la séance est saisie après coup. Le cycle de vie complet (déclaration, qualification, rattrapage,
 * annulation) est dans le contexte absences-patients (slice A2).
 */
public final class AbsencePatient {

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

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
