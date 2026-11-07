package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Observation biologique codée LOINC d'un patient (table {@code observations_biologiques}) : le modèle générique, qui coexiste sans
 * projection avec le bilan à colonnes fixes {@link Analyse}. Racine d'agrégat à une seule entité.
 * <p>
 * Invariants : l'analyte est codé LOINC ; la valeur est soit numérique AVEC unité, soit textuelle, jamais les deux ni aucune ; la date de
 * prélèvement est obligatoire et n'est jamais dans le futur (jour de l'horloge) ; une observation dérivée d'un bilan est immuable ; le
 * patient, le centre, l'analyte, la date et la demande d'examen ne changent jamais ; toute correction passe le statut à CORRIGE. Bornes de
 * colonne (400 au lieu du 500 du legacy) : libellé 255, texte 500, unité 20, valeur {@code numeric(14,4)}.
 * <p>
 * Reproduit (à confirmer avec les métiers et le DPO) : aucune règle de valeur (KB-MED-79) ; un texte blanc à côté d'une valeur numérique est
 * conservé tel quel ; la demande d'examen est une référence souple, non contrôlée à l'écriture (KB-MED-77) ; aucune interprétation
 * normal / anormal ; aucun événement (le legacy en collectait sans jamais les publier).
 */
public final class ObservationBiologique {
    static final int TEXTE_MAXIMAL = 500;

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final UUID demandeDExamen;
    private final CodeClinique analyte;
    private final LocalDate datePrelevement;
    private final SourceDObservation source;
    private final Instant creeLe;
    private ValeurMesuree valeurNumerique;
    private String valeurTexte;
    private StatutDObservation statut;
    private Instant modifieLe;

    private ObservationBiologique(UUID id, PatientId patient, CentreId centre, UUID demandeDExamen, CodeClinique analyte,
                                  ValeurMesuree valeurNumerique, String valeurTexte, LocalDate datePrelevement, StatutDObservation statut,
                                  SourceDObservation source, Instant creeLe, Instant modifieLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.demandeDExamen = demandeDExamen;
        this.analyte = analyte;
        this.valeurNumerique = valeurNumerique;
        this.valeurTexte = valeurTexte;
        this.datePrelevement = datePrelevement;
        this.statut = statut;
        this.source = source;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    /** Enregistre une observation saisie directement. {@code statut} absent : FINAL. Ordre des règles : celui du legacy. */
    public static ObservationBiologique enregistrer(UUID id, PatientId patient, CentreId centre, UUID demandeDExamen, CodeClinique analyte,
                                                    ValeurMesuree valeurNumerique, String valeurTexte, LocalDate datePrelevement,
                                                    StatutDObservation statut, LocalDate aujourdhui, Instant maintenant) {
        if (datePrelevement == null) {
            throw new RegleDeGestionViolee("OBSERVATION_CHAMPS_REQUIS", "Patient, centre, analyte et date de prélèvement sont obligatoires");
        }
        if (analyte.systeme() != SystemeDeCodage.LOINC) {
            throw new RegleDeGestionViolee("OBSERVATION_ANALYTE_NON_LOINC", "Une observation biologique doit être codée LOINC");
        }
        verifierLaValeur(valeurNumerique, valeurTexte);
        if (datePrelevement.isAfter(aujourdhui)) {
            throw new RegleDeGestionViolee("OBSERVATION_DATE_FUTURE", "La date de prélèvement ne peut pas être dans le futur");
        }
        analyte.verifierLesBornes();
        verifierLesBornes(valeurNumerique, valeurTexte);
        return new ObservationBiologique(id, patient, centre, demandeDExamen, analyte, valeurNumerique, valeurTexte, datePrelevement,
                statut != null ? statut : StatutDObservation.FINAL, SourceDObservation.SAISIE_DIRECTE, maintenant, maintenant);
    }

    public static ObservationBiologique reconstituer(UUID id, PatientId patient, CentreId centre, UUID demandeDExamen, CodeClinique analyte,
                                                     ValeurMesuree valeurNumerique, String valeurTexte, LocalDate datePrelevement,
                                                     StatutDObservation statut, SourceDObservation source, Instant creeLe, Instant modifieLe) {
        return new ObservationBiologique(id, patient, centre, demandeDExamen, analyte, valeurNumerique, valeurTexte, datePrelevement, statut,
                source, creeLe, modifieLe);
    }

    /** Remplace la valeur (numérique ou textuelle) et passe le statut à CORRIGE, même si la valeur est identique (KB-MED-84, reproduit). */
    public void corriger(ValeurMesuree valeurNumerique, String valeurTexte, Instant maintenant) {
        if (source == SourceDObservation.DERIVEE_BILAN) {
            throw new RegleDeGestionViolee("OBSERVATION_DERIVEE_IMMUABLE",
                    "Une observation dérivée automatiquement d'un bilan ne peut pas être modifiée ici");
        }
        verifierLaValeur(valeurNumerique, valeurTexte);
        verifierLesBornes(valeurNumerique, valeurTexte);
        this.valeurNumerique = valeurNumerique;
        this.valeurTexte = valeurTexte;
        this.statut = StatutDObservation.CORRIGE;
        this.modifieLe = maintenant;
    }

    private static void verifierLaValeur(ValeurMesuree valeurNumerique, String valeurTexte) {
        boolean numerique = valeurNumerique != null;
        boolean textuelle = valeurTexte != null && !valeurTexte.isBlank();
        if (numerique == textuelle) {
            throw new RegleDeGestionViolee("OBSERVATION_VALEUR_INVALIDE",
                    "Une observation porte soit une valeur numérique, soit une valeur textuelle, jamais les deux ni aucune");
        }
    }

    private static void verifierLesBornes(ValeurMesuree valeurNumerique, String valeurTexte) {
        if (valeurNumerique != null) valeurNumerique.verifierLesBornes();
        if (valeurTexte != null && valeurTexte.length() > TEXTE_MAXIMAL) throw new SaisieInvalide("Le texte est limité à 500 caractères");
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

    /** Demande d'examen visée (référence souple : aucune clé étrangère), ou nul. */
    public UUID demandeDExamen() {
        return demandeDExamen;
    }

    public CodeClinique analyte() {
        return analyte;
    }

    /** Valeur numérique avec son unité, ou nul. */
    public ValeurMesuree valeurNumerique() {
        return valeurNumerique;
    }

    public String valeurTexte() {
        return valeurTexte;
    }

    public LocalDate datePrelevement() {
        return datePrelevement;
    }

    public StatutDObservation statut() {
        return statut;
    }

    public SourceDObservation source() {
        return source;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
