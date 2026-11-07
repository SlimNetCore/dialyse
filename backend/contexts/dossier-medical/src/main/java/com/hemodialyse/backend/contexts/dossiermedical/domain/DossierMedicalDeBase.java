package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dossier médical de base d'un patient : néphropathie initiale, mise en dialyse, statuts d'hépatite B et C, observation globale et
 * conclusion médicale. Un dossier par patient. Chaque enregistrement REMPLACE tout le contenu (un champ absent est effacé, reproduit
 * du legacy) ; l'identifiant et la date de création sont conservés.
 * <p>
 * Bornes de colonne (écart décidé KB-MED-99, avant : 500) : néphropathie 255, statuts d'hépatite 20. Les statuts d'hépatite restent
 * libres (valeur quelconque, reproduit, à décider avec les médecins).
 */
public final class DossierMedicalDeBase {
    public static final int BORNE_NEPHROPATHIE = 255;
    public static final int BORNE_STATUT_D_HEPATITE = 20;

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final Instant creeLe;
    private String nephropathieInitiale;
    private LocalDate dateMiseEnDialyse;
    private String hepatiteBStatut;
    private String hepatiteCStatut;
    private String observationGlobale;
    private String conclusionMedicale;
    private Instant modifieLe;

    private DossierMedicalDeBase(UUID id, PatientId patient, CentreId centre, Instant creeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.creeLe = creeLe;
    }

    public static DossierMedicalDeBase ouvrir(UUID id, PatientId patient, CentreId centre, String nephropathieInitiale,
                                              LocalDate dateMiseEnDialyse, String hepatiteBStatut, String hepatiteCStatut,
                                              String observationGlobale, String conclusionMedicale, Instant maintenant) {
        DossierMedicalDeBase dossier = new DossierMedicalDeBase(id, patient, centre, maintenant);
        dossier.remplacer(nephropathieInitiale, dateMiseEnDialyse, hepatiteBStatut, hepatiteCStatut, observationGlobale, conclusionMedicale,
                maintenant);
        return dossier;
    }

    public static DossierMedicalDeBase reconstituer(UUID id, PatientId patient, CentreId centre, String nephropathieInitiale,
                                                    LocalDate dateMiseEnDialyse, String hepatiteBStatut, String hepatiteCStatut,
                                                    String observationGlobale, String conclusionMedicale, Instant creeLe, Instant modifieLe) {
        DossierMedicalDeBase dossier = new DossierMedicalDeBase(id, patient, centre, creeLe);
        dossier.nephropathieInitiale = nephropathieInitiale;
        dossier.dateMiseEnDialyse = dateMiseEnDialyse;
        dossier.hepatiteBStatut = hepatiteBStatut;
        dossier.hepatiteCStatut = hepatiteCStatut;
        dossier.observationGlobale = observationGlobale;
        dossier.conclusionMedicale = conclusionMedicale;
        dossier.modifieLe = modifieLe;
        return dossier;
    }

    /** Remplace tout le contenu du dossier ; refuse une valeur plus longue que sa colonne (400). */
    public void remplacer(String nephropathieInitiale, LocalDate dateMiseEnDialyse, String hepatiteBStatut, String hepatiteCStatut,
                          String observationGlobale, String conclusionMedicale, Instant maintenant) {
        borner(nephropathieInitiale, BORNE_NEPHROPATHIE, "nephropathieInitiale");
        borner(hepatiteBStatut, BORNE_STATUT_D_HEPATITE, "hepatiteBStatut");
        borner(hepatiteCStatut, BORNE_STATUT_D_HEPATITE, "hepatiteCStatut");
        this.nephropathieInitiale = nephropathieInitiale;
        this.dateMiseEnDialyse = dateMiseEnDialyse;
        this.hepatiteBStatut = hepatiteBStatut;
        this.hepatiteCStatut = hepatiteCStatut;
        this.observationGlobale = observationGlobale;
        this.conclusionMedicale = conclusionMedicale;
        this.modifieLe = maintenant;
    }

    private static void borner(String valeur, int borne, String champ) {
        if (valeur != null && valeur.length() > borne) {
            throw new ValeurHorsBorne("Le champ " + champ + " dépasse " + borne + " caractères");
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

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }

    public String nephropathieInitiale() {
        return nephropathieInitiale;
    }

    public LocalDate dateMiseEnDialyse() {
        return dateMiseEnDialyse;
    }

    public String hepatiteBStatut() {
        return hepatiteBStatut;
    }

    public String hepatiteCStatut() {
        return hepatiteCStatut;
    }

    public String observationGlobale() {
        return observationGlobale;
    }

    public String conclusionMedicale() {
        return conclusionMedicale;
    }
}
