package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Antécédent ou comorbidité d'un patient (table {@code antecedents_medicaux}). Racine d'agrégat à une seule entité.
 * <p>
 * Invariants : un diagnostic codé OU un libellé libre est obligatoire ; la période est cohérente (début obligatoire, fin pas avant le
 * début) ; la création refuse un début dans le futur (jour de l'horloge fournie) ; un antécédent déjà résolu ne se résout pas deux fois.
 * Unicité d'un diagnostic actif par patient : règle de l'application, sous verrou (le legacy ne la protégeait pas, KB-MED-64).
 * Bornes de colonne (400 au lieu du 500 du legacy) : sévérité 50 caractères, libellé libre 255.
 * <p>
 * Reproduit (KB-MED-65, à confirmer avec les métiers et le DPO) : la correction REMPLACE diagnostic, libellé, dates, sévérité et note
 * (absents = effacés), ne contrôle pas le futur, peut poser RESOLU sans date de fin et rouvrir un antécédent résolu ; le type ne change
 * jamais ; la résolution accepte une date absente ou future. Aucun événement {@code AntecedentAjoute} / {@code AntecedentResolu} : le
 * legacy les collectait sans jamais les publier (KB-MED-67, à confirmer).
 */
public final class Antecedent {
    static final int SEVERITE_MAXIMALE = 50;
    static final int LIBELLE_MAXIMAL = 255;

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final TypeDAntecedent type;
    private final Instant creeLe;
    private CodeClinique diagnostic;
    private String libelleLibre;
    private LocalDate debut;
    private LocalDate fin;
    private StatutClinique statut;
    private String severite;
    private String note;
    private Instant modifieLe;

    private Antecedent(UUID id, PatientId patient, CentreId centre, TypeDAntecedent type, CodeClinique diagnostic, String libelleLibre,
                       LocalDate debut, LocalDate fin, StatutClinique statut, String severite, String note, Instant creeLe,
                       Instant modifieLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.type = type;
        this.diagnostic = diagnostic;
        this.libelleLibre = libelleLibre;
        this.debut = debut;
        this.fin = fin;
        this.statut = statut;
        this.severite = severite;
        this.note = note;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    /** Enregistre un antécédent actif. {@code aujourdhui} est le jour de référence pour refuser un début futur. */
    public static Antecedent enregistrer(UUID id, PatientId patient, CentreId centre, TypeDAntecedent type, CodeClinique diagnostic,
                                         String libelleLibre, LocalDate debut, LocalDate fin, String severite, String note,
                                         LocalDate aujourdhui, Instant maintenant) {
        exigerUnDiagnostic(diagnostic, libelleLibre);
        verifierLaPeriode(debut, fin);
        if (debut.isAfter(aujourdhui)) {
            throw new RegleDeGestionViolee("ANTECEDENT_DATE_FUTURE", "La date de début ne peut pas être dans le futur");
        }
        String libelle = libelleLibre == null ? null : libelleLibre.trim();
        verifierLesBornes(diagnostic, libelle, severite);
        return new Antecedent(id, patient, centre, type, diagnostic, libelle, debut, fin, StatutClinique.ACTIF, severite, note, maintenant,
                maintenant);
    }

    public static Antecedent reconstituer(UUID id, PatientId patient, CentreId centre, TypeDAntecedent type, CodeClinique diagnostic,
                                          String libelleLibre, LocalDate debut, LocalDate fin, StatutClinique statut, String severite,
                                          String note, Instant creeLe, Instant modifieLe) {
        return new Antecedent(id, patient, centre, type, diagnostic, libelleLibre, debut, fin, statut, severite, note, creeLe, modifieLe);
    }

    /** Corrige la fiche : tout est remplacé par la saisie sauf le type ; un statut absent laisse le statut inchangé. */
    public void corriger(CodeClinique diagnostic, String libelleLibre, LocalDate debut, LocalDate fin, StatutClinique statut,
                         String severite, String note, Instant maintenant) {
        exigerUnDiagnostic(diagnostic, libelleLibre);
        verifierLaPeriode(debut, fin);
        String libelle = libelleLibre == null ? null : libelleLibre.trim();
        verifierLesBornes(diagnostic, libelle, severite);
        this.diagnostic = diagnostic;
        this.libelleLibre = libelle;
        this.debut = debut;
        this.fin = fin;
        if (statut != null) this.statut = statut;
        this.severite = severite;
        this.note = note;
        this.modifieLe = maintenant;
    }

    /** Résout l'antécédent à la date donnée (absente ou future acceptée : reproduit). */
    public void resoudre(LocalDate dateDeResolution, Instant maintenant) {
        if (statut == StatutClinique.RESOLU) {
            throw new RegleDeGestionViolee("ANTECEDENT_DEJA_RESOLU", "Cet antécédent est déjà résolu");
        }
        verifierLaPeriode(debut, dateDeResolution);
        this.fin = dateDeResolution;
        this.statut = StatutClinique.RESOLU;
        this.modifieLe = maintenant;
    }

    private static void exigerUnDiagnostic(CodeClinique diagnostic, String libelleLibre) {
        if (diagnostic == null && (libelleLibre == null || libelleLibre.isBlank())) {
            throw new RegleDeGestionViolee("ANTECEDENT_DIAGNOSTIC_REQUIS", "Un code CIM-10 ou, à défaut, un libellé est obligatoire");
        }
    }

    private static void verifierLaPeriode(LocalDate debut, LocalDate fin) {
        if (debut == null) throw new RegleDeGestionViolee("PERIODE_DEBUT_REQUIS", "La date de début est obligatoire");
        if (fin != null && fin.isBefore(debut)) {
            throw new RegleDeGestionViolee("PERIODE_FIN_ANTERIEURE", "La date de fin ne peut pas précéder la date de début");
        }
    }

    private static void verifierLesBornes(CodeClinique diagnostic, String libelle, String severite) {
        if (diagnostic != null) diagnostic.verifierLesBornes();
        if (libelle != null && libelle.length() > LIBELLE_MAXIMAL) throw new SaisieInvalide("Le libellé est limité à 255 caractères");
        if (severite != null && severite.length() > SEVERITE_MAXIMALE) throw new SaisieInvalide("La sévérité est limitée à 50 caractères");
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

    public TypeDAntecedent type() {
        return type;
    }

    public CodeClinique diagnostic() {
        return diagnostic;
    }

    public String libelleLibre() {
        return libelleLibre;
    }

    public LocalDate debut() {
        return debut;
    }

    public LocalDate fin() {
        return fin;
    }

    public StatutClinique statut() {
        return statut;
    }

    public String severite() {
        return severite;
    }

    public String note() {
        return note;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
