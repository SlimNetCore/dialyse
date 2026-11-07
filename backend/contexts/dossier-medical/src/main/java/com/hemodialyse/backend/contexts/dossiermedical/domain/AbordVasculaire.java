package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Abord vasculaire d'un patient (fistule, pontage, cathéter : table {@code abords_vasculaires}). Racine d'agrégat à une seule entité.
 * <p>
 * Invariants : un abord ne peut pas être retiré avant d'avoir été créé (quand les deux dates sont connues) ; le type est obligatoire ;
 * un abord est actif sauf indication contraire. Bornes de colonne (400 au lieu du 500 du legacy, KB-MED-58 et 63) : type 20 caractères,
 * côté 10, localisation 255, complications 500.
 * <p>
 * Reproduit (KB-MED-63, à confirmer avec les métiers et le DPO) : le type est un TEXTE LIBRE (vide accepté ; la Planification n'exige la
 * compétence CATHETER que pour {@code KT_TUNNELISE} et {@code KT_AIGU} en majuscules, abord actif et non terminé, et ignore tout autre
 * texte : ce contexte ne la modifie pas) ; aucune cohérence entre « actif » et la date de fin ; ni limite de date, ni date obligatoire ;
 * le remplacement efface les champs absents, réactive l'abord si « actif » est absent et RÉÉCRIT la date d'enregistrement (la seule
 * colonne horodatée, rendue comme {@code updatedAt} par l'écriture). Aucun événement (le legacy n'en avait pas).
 */
public final class AbordVasculaire {
    static final int TYPE_MAXIMAL = 20;
    static final int COTE_MAXIMALE = 10;
    static final int LOCALISATION_MAXIMALE = 255;
    static final int COMPLICATIONS_MAXIMALES = 500;

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private String type;
    private String cote;
    private String localisation;
    private LocalDate creation;
    private LocalDate fin;
    private boolean actif;
    private String complications;
    private Instant enregistreLe;

    private AbordVasculaire(UUID id, PatientId patient, CentreId centre, String type, String cote, String localisation, LocalDate creation,
                            LocalDate fin, boolean actif, String complications, Instant enregistreLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.type = type;
        this.cote = cote;
        this.localisation = localisation;
        this.creation = creation;
        this.fin = fin;
        this.actif = actif;
        this.complications = complications;
        this.enregistreLe = enregistreLe;
    }

    /** Enregistre un abord. {@code actif} absent : actif. */
    public static AbordVasculaire enregistrer(UUID id, PatientId patient, CentreId centre, String type, String cote, String localisation,
                                              LocalDate creation, LocalDate fin, Boolean actif, String complications, Instant maintenant) {
        verifier(type, cote, localisation, creation, fin, complications);
        return new AbordVasculaire(id, patient, centre, type, cote, localisation, creation, fin, actif == null || actif, complications,
                maintenant);
    }

    public static AbordVasculaire reconstituer(UUID id, PatientId patient, CentreId centre, String type, String cote, String localisation,
                                               LocalDate creation, LocalDate fin, boolean actif, String complications, Instant enregistreLe) {
        return new AbordVasculaire(id, patient, centre, type, cote, localisation, creation, fin, actif, complications, enregistreLe);
    }

    /**
     * Remplace tous les champs par la saisie (absents = effacés ; {@code actif} absent = actif) et réécrit la date d'enregistrement.
     * L'abord reste celui du même patient et du même centre : il n'est jamais réaffecté (KB-MED-62 corrigé).
     */
    public void remplacer(String type, String cote, String localisation, LocalDate creation, LocalDate fin, Boolean actif,
                          String complications, Instant maintenant) {
        verifier(type, cote, localisation, creation, fin, complications);
        this.type = type;
        this.cote = cote;
        this.localisation = localisation;
        this.creation = creation;
        this.fin = fin;
        this.actif = actif == null || actif;
        this.complications = complications;
        this.enregistreLe = maintenant;
    }

    private static void verifier(String type, String cote, String localisation, LocalDate creation, LocalDate fin, String complications) {
        if (creation != null && fin != null && fin.isBefore(creation)) {
            throw new RegleDeGestionViolee("ABORD_DATE_FIN_ANTERIEURE",
                    "La date de fin de l'abord vasculaire ne peut pas précéder sa date de création");
        }
        if (type == null) throw new SaisieInvalide("Le type d'abord est requis");
        if (type.length() > TYPE_MAXIMAL) throw new SaisieInvalide("Le type d'abord est limité à 20 caractères");
        if (cote != null && cote.length() > COTE_MAXIMALE) throw new SaisieInvalide("Le côté est limité à 10 caractères");
        if (localisation != null && localisation.length() > LOCALISATION_MAXIMALE) {
            throw new SaisieInvalide("La localisation est limitée à 255 caractères");
        }
        if (complications != null && complications.length() > COMPLICATIONS_MAXIMALES) {
            throw new SaisieInvalide("Les complications sont limitées à 500 caractères");
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

    public String type() {
        return type;
    }

    public String cote() {
        return cote;
    }

    public String localisation() {
        return localisation;
    }

    public LocalDate creation() {
        return creation;
    }

    public LocalDate fin() {
        return fin;
    }

    public boolean actif() {
        return actif;
    }

    public String complications() {
        return complications;
    }

    public Instant enregistreLe() {
        return enregistreLe;
    }
}
