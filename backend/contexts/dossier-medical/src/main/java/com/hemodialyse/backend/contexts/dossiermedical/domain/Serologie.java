package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Résultat sérologique d'un patient pour un marqueur et une date de prélèvement (table {@code serologies_patient}). Racine
 * d'agrégat à une seule entité.
 * <p>
 * Invariants : un résultat POSITIF impose une conduite à tenir non blanche ; pas de date de prélèvement dans le futur (jour du
 * centre) ; l'identité du résultat (marqueur, date, titre, laboratoire) ne change jamais, seuls le résultat et la conduite à tenir se
 * corrigent. Bornes des colonnes (400 au lieu du 500 du legacy, KB-MED-52) : unité 20 caractères, laboratoire 255, titre arrondi à 3
 * décimales et d'au plus 7 chiffres entiers. Reproduit (à confirmer avec les métiers et le DPO) : titre négatif accepté, prochain
 * contrôle antérieur au prélèvement accepté, aucune date minimale, conduite à tenir sans limite (KB-MED-52), la correction remplace la
 * conduite à tenir même absente (KB-MED-51).
 * <p>
 * Aucun événement {@code SerologiePositiveDetectee} : le legacy le levait sans jamais le publier (KB-MED-46, à confirmer avec les
 * métiers) ; le seul effet d'un résultat est la réévaluation de la place du patient par la Planification (port de l'application).
 */
public final class Serologie {

    static final int UNITE_MAXIMALE = 20;
    static final int LABORATOIRE_MAXIMAL = 255;
    static final BigDecimal TITRE_EXCLU = BigDecimal.TEN.pow(7);

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final MarqueurSerologique marqueur;
    private final BigDecimal titre;
    private final String unite;
    private final LocalDate datePrelevement;
    private final String laboratoire;
    private final LocalDate dateProchainControle;
    private final Instant creeeLe;
    private ResultatSerologique resultat;
    private String conduiteATenir;
    private Instant modifieeLe;

    private Serologie(UUID id, PatientId patient, CentreId centre, MarqueurSerologique marqueur, ResultatSerologique resultat,
                      BigDecimal titre, String unite, LocalDate datePrelevement, String laboratoire, LocalDate dateProchainControle,
                      String conduiteATenir, Instant creeeLe, Instant modifieeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.marqueur = marqueur;
        this.resultat = resultat;
        this.titre = titre;
        this.unite = unite;
        this.datePrelevement = datePrelevement;
        this.laboratoire = laboratoire;
        this.dateProchainControle = dateProchainControle;
        this.conduiteATenir = conduiteATenir;
        this.creeeLe = creeeLe;
        this.modifieeLe = modifieeLe;
    }

    /**
     * Enregistre un résultat.
     *
     * @param aujourdhui le jour de référence (celui du centre) pour refuser une date future
     */
    public static Serologie enregistrer(UUID id, PatientId patient, CentreId centre, MarqueurSerologique marqueur,
                                        ResultatSerologique resultat, BigDecimal titre, String unite, LocalDate datePrelevement,
                                        LocalDate aujourdhui, String laboratoire, LocalDate dateProchainControle, String conduiteATenir,
                                        Instant maintenant) {
        if (patient == null || centre == null || marqueur == null || resultat == null || datePrelevement == null) {
            throw new RegleDeGestionViolee("SEROLOGIE_CHAMPS_REQUIS",
                    "Patient, centre, marqueur, résultat et date de prélèvement sont obligatoires");
        }
        if (datePrelevement.isAfter(aujourdhui)) {
            throw new RegleDeGestionViolee("SEROLOGIE_DATE_FUTURE", "La date de prélèvement ne peut pas être dans le futur");
        }
        verifierLaConduite(resultat, conduiteATenir);
        BigDecimal titreArrondi = titre != null && titre.scale() > 3 ? titre.setScale(3, RoundingMode.HALF_UP) : titre;
        if (titreArrondi != null && titreArrondi.abs().compareTo(TITRE_EXCLU) >= 0) {
            throw new SaisieInvalide("Le titre est limité à 7 chiffres entiers");
        }
        if (unite != null && unite.length() > UNITE_MAXIMALE) throw new SaisieInvalide("L'unité est limitée à 20 caractères");
        if (laboratoire != null && laboratoire.length() > LABORATOIRE_MAXIMAL) {
            throw new SaisieInvalide("Le laboratoire est limité à 255 caractères");
        }
        return new Serologie(id, patient, centre, marqueur, resultat, titreArrondi, unite, datePrelevement, laboratoire,
                dateProchainControle, conduiteATenir, maintenant, maintenant);
    }

    public static Serologie reconstituer(UUID id, PatientId patient, CentreId centre, MarqueurSerologique marqueur,
                                         ResultatSerologique resultat, BigDecimal titre, String unite, LocalDate datePrelevement,
                                         String laboratoire, LocalDate dateProchainControle, String conduiteATenir, Instant creeeLe,
                                         Instant modifieeLe) {
        return new Serologie(id, patient, centre, marqueur, resultat, titre, unite, datePrelevement, laboratoire, dateProchainControle,
                conduiteATenir, creeeLe, modifieeLe);
    }

    /** Corrige le résultat et la conduite à tenir (celle-ci est remplacée, même absente : KB-MED-51 reproduit). */
    public void corrigerLeResultat(ResultatSerologique nouveau, String conduiteATenir, Instant maintenant) {
        if (nouveau == null) throw new SaisieInvalide("Le résultat de la sérologie est requis");
        verifierLaConduite(nouveau, conduiteATenir);
        this.resultat = nouveau;
        this.conduiteATenir = conduiteATenir;
        this.modifieeLe = maintenant;
    }

    private static void verifierLaConduite(ResultatSerologique resultat, String conduiteATenir) {
        if (resultat.exigeUneConduiteATenir() && (conduiteATenir == null || conduiteATenir.isBlank())) {
            throw new RegleDeGestionViolee("SEROLOGIE_CONDUITE_REQUISE", "Un résultat positif impose de documenter la conduite à tenir");
        }
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

    public MarqueurSerologique marqueur() {
        return marqueur;
    }

    public ResultatSerologique resultat() {
        return resultat;
    }

    public BigDecimal titre() {
        return titre;
    }

    public String unite() {
        return unite;
    }

    public LocalDate datePrelevement() {
        return datePrelevement;
    }

    public String laboratoire() {
        return laboratoire;
    }

    public LocalDate dateProchainControle() {
        return dateProchainControle;
    }

    public String conduiteATenir() {
        return conduiteATenir;
    }

    public Instant creeeLe() {
        return creeeLe;
    }

    public Instant modifieeLe() {
        return modifieeLe;
    }
}
