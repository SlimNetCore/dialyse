package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Demande d'examen d'un patient (biologie, imagerie, fonctionnel, anapath), suivie jusqu'à sa validation. Racine d'agrégat ; ses
 * lignes en font partie et ne changent plus après la demande. Les résultats sont des observations biologiques (DM6), reliées par
 * l'identité de la demande : ils ne pilotent JAMAIS le statut (reproduit, KB-MED-37 : « résultat disponible » est une action
 * manuelle du médecin, aucun événement n'est publié).
 * <p>
 * Invariants : au moins une ligne ; pas de date de demande dans le futur (jour du centre) ; les changements de statut suivent
 * {@link StatutDeDemandeDExamen}. Reproduit (KB-MED-42, à confirmer avec les métiers) : la conclusion n'est posée que par la
 * validation et n'est pas contrôlée ; le prescripteur est un texte libre, non lié au compte connecté ; aucune date minimale.
 */
public final class DemandeExamen {

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final String prescripteur;
    private final LocalDate dateDemande;
    private final CategorieDExamen categorie;
    private final boolean urgent;
    private final String motif;
    private final List<LigneDeDemandeDExamen> lignes;
    private final Instant creeeLe;
    private StatutDeDemandeDExamen statut;
    private String conclusion;
    private Instant modifieeLe;

    private DemandeExamen(UUID id, PatientId patient, CentreId centre, String prescripteur, LocalDate dateDemande,
                          CategorieDExamen categorie, boolean urgent, String motif, StatutDeDemandeDExamen statut, String conclusion,
                          List<LigneDeDemandeDExamen> lignes, Instant creeeLe, Instant modifieeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.prescripteur = prescripteur;
        this.dateDemande = dateDemande;
        this.categorie = categorie;
        this.urgent = urgent;
        this.motif = motif;
        this.statut = statut;
        this.conclusion = conclusion;
        this.lignes = List.copyOf(lignes);
        this.creeeLe = creeeLe;
        this.modifieeLe = modifieeLe;
    }

    /**
     * Demande un examen : la demande naît à l'état DEMANDE, sans conclusion.
     *
     * @param aujourdhui le jour de référence pour refuser une date future
     */
    public static DemandeExamen demander(UUID id, PatientId patient, CentreId centre, String prescripteur, LocalDate dateDemande,
                                         LocalDate aujourdhui, CategorieDExamen categorie, boolean urgent, String motif,
                                         List<LigneDeDemandeDExamen> lignes, Instant maintenant) {
        if (patient == null || centre == null || categorie == null) {
            throw new RegleDeGestionViolee("DEMANDE_EXAMEN_CHAMPS_REQUIS", "Patient, centre et catégorie sont obligatoires");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new RegleDeGestionViolee("DEMANDE_EXAMEN_LIGNE_REQUISE", "Une demande d'examen doit comporter au moins une ligne");
        }
        if (dateDemande.isAfter(aujourdhui)) {
            throw new RegleDeGestionViolee("DEMANDE_EXAMEN_DATE_FUTURE", "La date de demande ne peut pas être dans le futur");
        }
        if (prescripteur != null && prescripteur.length() > 100) throw new SaisieInvalide("Le prescripteur est limité à 100 caractères");
        if (motif != null && motif.length() > 500) throw new SaisieInvalide("Le motif est limité à 500 caractères");
        return new DemandeExamen(id, patient, centre, prescripteur, dateDemande, categorie, urgent, motif,
                StatutDeDemandeDExamen.DEMANDE, null, lignes, maintenant, maintenant);
    }

    public static DemandeExamen reconstituer(UUID id, PatientId patient, CentreId centre, String prescripteur, LocalDate dateDemande,
                                             CategorieDExamen categorie, boolean urgent, String motif, StatutDeDemandeDExamen statut,
                                             String conclusion, List<LigneDeDemandeDExamen> lignes, Instant creeeLe, Instant modifieeLe) {
        return new DemandeExamen(id, patient, centre, prescripteur, dateDemande, categorie, urgent, motif, statut, conclusion, lignes,
                creeeLe, modifieeLe);
    }

    /** Le prélèvement a été effectué. */
    public void enregistrerLePrelevement(Instant maintenant) {
        passerA(StatutDeDemandeDExamen.PRELEVE, maintenant);
    }

    /** Le médecin déclare le résultat disponible (action manuelle : la saisie d'une observation ne le fait pas). */
    public void rendreLeResultatDisponible(Instant maintenant) {
        passerA(StatutDeDemandeDExamen.RESULTAT_DISPONIBLE, maintenant);
    }

    /** Le médecin valide les résultats et clôt la demande ; la conclusion est facultative et libre (reproduit). */
    public void valider(String conclusion, Instant maintenant) {
        passerA(StatutDeDemandeDExamen.VALIDE, maintenant);
        this.conclusion = conclusion;
    }

    /** Annule la demande, tant que le résultat n'est pas disponible. */
    public void annuler(Instant maintenant) {
        passerA(StatutDeDemandeDExamen.ANNULE, maintenant);
    }

    private void passerA(StatutDeDemandeDExamen vers, Instant maintenant) {
        if (!statut.peutPasserA(vers)) {
            throw new RegleDeGestionViolee("DEMANDE_EXAMEN_TRANSITION_INVALIDE", "Transition de statut invalide : " + statut + " → " + vers);
        }
        this.statut = vers;
        this.modifieeLe = maintenant;
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

    public String prescripteur() {
        return prescripteur;
    }

    public LocalDate dateDemande() {
        return dateDemande;
    }

    public CategorieDExamen categorie() {
        return categorie;
    }

    public boolean urgent() {
        return urgent;
    }

    public String motif() {
        return motif;
    }

    public StatutDeDemandeDExamen statut() {
        return statut;
    }

    public String conclusion() {
        return conclusion;
    }

    public List<LigneDeDemandeDExamen> lignes() {
        return lignes;
    }

    public Instant creeeLe() {
        return creeeLe;
    }

    public Instant modifieeLe() {
        return modifieeLe;
    }
}
