package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Candidat donneur vivant d'un patient receveur (table {@code donneurs_vivants}). Racine d'agrégat à une seule entité.
 * <p>
 * Invariants : nom non blanc et lien de parenté obligatoires (422 {@code DONNEUR_VIVANT_CHAMPS_REQUIS}, reproduit) ; à l'enregistrement le
 * statut est {@code CANDIDAT} et le crossmatch {@code NON_FAIT} ; bornes de colonne en 400 (nom et prénom 100, téléphone 30, groupe sanguin
 * 5, typage HLA 500). Reproduit (KB-MED-93, à confirmer) : la mise à jour REMPLACE tout (champ absent effacé), aucune règle croisée (statut
 * {@code RETENU} avec crossmatch {@code POSITIF}, naissance dans le futur, crossmatch positif sans date acceptés).
 */
public final class DonneurVivant {
    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final Instant creeLe;
    private String nom;
    private String prenom;
    private LocalDate dateNaissance;
    private LienDeParente lienDeParente;
    private String telephone;
    private String groupeSanguin;
    private String typageHla;
    private StatutDuBilanDuDonneur statutBilan;
    private ResultatDeCrossmatch crossmatch;
    private LocalDate dateCrossmatch;
    private String bilanRealise;
    private String contreIndications;
    private String decisionFinale;
    private LocalDate dateDecision;
    private Instant modifieLe;

    private DonneurVivant(UUID id, PatientId patient, CentreId centre, Instant creeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.creeLe = creeLe;
    }

    public static DonneurVivant enregistrer(UUID id, PatientId patient, CentreId centre, String nom, String prenom, LocalDate dateNaissance,
                                            LienDeParente lienDeParente, String telephone, String groupeSanguin, String typageHla,
                                            Instant maintenant) {
        DonneurVivant donneur = new DonneurVivant(id, patient, centre, maintenant);
        donneur.remplirLIdentite(nom, prenom, dateNaissance, lienDeParente, telephone, groupeSanguin, typageHla);
        donneur.statutBilan = StatutDuBilanDuDonneur.CANDIDAT;
        donneur.crossmatch = ResultatDeCrossmatch.NON_FAIT;
        donneur.modifieLe = maintenant;
        return donneur;
    }

    public static DonneurVivant reconstituer(UUID id, PatientId patient, CentreId centre, String nom, String prenom, LocalDate dateNaissance,
                                             LienDeParente lienDeParente, String telephone, String groupeSanguin, String typageHla,
                                             StatutDuBilanDuDonneur statutBilan, ResultatDeCrossmatch crossmatch, LocalDate dateCrossmatch,
                                             String bilanRealise, String contreIndications, String decisionFinale, LocalDate dateDecision,
                                             Instant creeLe, Instant modifieLe) {
        DonneurVivant d = new DonneurVivant(id, patient, centre, creeLe);
        d.nom = nom;
        d.prenom = prenom;
        d.dateNaissance = dateNaissance;
        d.lienDeParente = lienDeParente;
        d.telephone = telephone;
        d.groupeSanguin = groupeSanguin;
        d.typageHla = typageHla;
        d.statutBilan = statutBilan;
        d.crossmatch = crossmatch;
        d.dateCrossmatch = dateCrossmatch;
        d.bilanRealise = bilanRealise;
        d.contreIndications = contreIndications;
        d.decisionFinale = decisionFinale;
        d.dateDecision = dateDecision;
        d.modifieLe = modifieLe;
        return d;
    }

    /** Remplace tout le dossier du donneur (statut du bilan obligatoire ; crossmatch absent refusé en 400 à l'interface). */
    public void mettreAJour(String nom, String prenom, LocalDate dateNaissance, LienDeParente lienDeParente, String telephone,
                            String groupeSanguin, String typageHla, StatutDuBilanDuDonneur statutBilan, ResultatDeCrossmatch crossmatch,
                            LocalDate dateCrossmatch, String bilanRealise, String contreIndications, String decisionFinale,
                            LocalDate dateDecision, Instant maintenant) {
        if (statutBilan == null) {
            throw new RegleDeGestionViolee("DONNEUR_VIVANT_CHAMPS_REQUIS", "Nom, lien de parenté et statut de bilan sont obligatoires");
        }
        remplirLIdentite(nom, prenom, dateNaissance, lienDeParente, telephone, groupeSanguin, typageHla);
        this.statutBilan = statutBilan;
        this.crossmatch = crossmatch != null ? crossmatch : ResultatDeCrossmatch.NON_FAIT;
        this.dateCrossmatch = dateCrossmatch;
        this.bilanRealise = bilanRealise;
        this.contreIndications = contreIndications;
        this.decisionFinale = decisionFinale;
        this.dateDecision = dateDecision;
        this.modifieLe = maintenant;
    }

    private void remplirLIdentite(String nom, String prenom, LocalDate dateNaissance, LienDeParente lien, String telephone,
                                  String groupeSanguin, String typageHla) {
        if (nom == null || nom.isBlank() || lien == null) {
            throw new RegleDeGestionViolee("DONNEUR_VIVANT_CHAMPS_REQUIS", "Patient, centre, nom et lien de parenté sont obligatoires");
        }
        borne(nom, 100, "Le nom");
        borne(prenom, 100, "Le prénom");
        borne(telephone, 30, "Le téléphone");
        borne(groupeSanguin, 5, "Le groupe sanguin");
        borne(typageHla, 500, "Le typage HLA");
        this.nom = nom;
        this.prenom = prenom;
        this.dateNaissance = dateNaissance;
        this.lienDeParente = lien;
        this.telephone = telephone;
        this.groupeSanguin = groupeSanguin;
        this.typageHla = typageHla;
    }

    private static void borne(String valeur, int max, String champ) {
        if (valeur != null && valeur.length() > max) throw new SaisieInvalide(champ + " dépasse " + max + " caractères");
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

    public String nom() {
        return nom;
    }

    public String prenom() {
        return prenom;
    }

    public LocalDate dateNaissance() {
        return dateNaissance;
    }

    public LienDeParente lienDeParente() {
        return lienDeParente;
    }

    public String telephone() {
        return telephone;
    }

    public String groupeSanguin() {
        return groupeSanguin;
    }

    public String typageHla() {
        return typageHla;
    }

    public StatutDuBilanDuDonneur statutBilan() {
        return statutBilan;
    }

    public ResultatDeCrossmatch crossmatch() {
        return crossmatch;
    }

    public LocalDate dateCrossmatch() {
        return dateCrossmatch;
    }

    public String bilanRealise() {
        return bilanRealise;
    }

    public String contreIndications() {
        return contreIndications;
    }

    public String decisionFinale() {
        return decisionFinale;
    }

    public LocalDate dateDecision() {
        return dateDecision;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
