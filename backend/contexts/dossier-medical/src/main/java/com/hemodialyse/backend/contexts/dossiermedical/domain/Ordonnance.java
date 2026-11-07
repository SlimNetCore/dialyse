package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ordonnance médicamenteuse d'un patient : un document destiné au patient et à la pharmacie, distinct de la prescription
 * médicale (paramètres de séance et traitement de l'anémie). Racine d'agrégat ; ses lignes en font partie et ne changent plus
 * après la création (aucun mutateur de contenu : corriger une ordonnance signée, c'est l'annuler et en créer une autre).
 * <p>
 * Invariants : au moins une ligne ; le numéro n'existe qu'une fois signée et ne change plus ; les transitions suivent
 * {@link StatutOrdonnance}. Le numéro est réservé AVANT le contrôle de la transition (cas d'usage) : un refus de signature
 * consomme donc un numéro, trou de la séquence légale REPRODUIT (KB-MED-20, décision du 07/11 : à confirmer avec les métiers).
 * Le prescripteur ({@code medecinId}) est un texte libre, non lié au compte
 * connecté (KB-MED-30, reproduit).
 */
public final class Ordonnance {

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final String medecin;
    private final LocalDate datePrescription;
    private final List<LigneDOrdonnance> lignes;
    private final Instant creeeLe;
    private StatutOrdonnance statut;
    private String numero;
    private Instant signeeLe;
    private Instant modifieeLe;

    private Ordonnance(UUID id, PatientId patient, CentreId centre, String medecin, LocalDate datePrescription,
                       StatutOrdonnance statut, String numero, List<LigneDOrdonnance> lignes, Instant creeeLe, Instant modifieeLe,
                       Instant signeeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.medecin = medecin;
        this.datePrescription = datePrescription;
        this.statut = statut;
        this.numero = numero;
        this.lignes = List.copyOf(lignes);
        this.creeeLe = creeeLe;
        this.modifieeLe = modifieeLe;
        this.signeeLe = signeeLe;
    }

    /** Rédige une ordonnance : un brouillon sans numéro. */
    public static Ordonnance rediger(UUID id, PatientId patient, CentreId centre, String medecin, LocalDate datePrescription,
                                     List<LigneDOrdonnance> lignes, Instant maintenant) {
        if (patient == null || centre == null) {
            throw new RegleDeGestionViolee("ORDONNANCE_CHAMPS_REQUIS", "Patient et centre sont obligatoires");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new RegleDeGestionViolee("ORDONNANCE_LIGNE_REQUISE", "Une ordonnance doit comporter au moins une ligne");
        }
        if (medecin != null && medecin.length() > 100) throw new SaisieInvalide("Le prescripteur est limité à 100 caractères");
        return new Ordonnance(id, patient, centre, medecin, datePrescription, StatutOrdonnance.BROUILLON, null, lignes, maintenant,
                maintenant, null);
    }

    public static Ordonnance reconstituer(UUID id, PatientId patient, CentreId centre, String medecin, LocalDate datePrescription,
                                          StatutOrdonnance statut, String numero, List<LigneDOrdonnance> lignes, Instant creeeLe,
                                          Instant modifieeLe, Instant signeeLe) {
        return new Ordonnance(id, patient, centre, medecin, datePrescription, statut, numero, lignes, creeeLe, modifieeLe, signeeLe);
    }

    /**
     * Signature médicale : rend l'ordonnance opposable et lui attribue son numéro définitif.
     *
     * @param attribue le numéro réservé dans la séquence du centre
     */
    public void signer(String attribue, Instant maintenant) {
        controler(StatutOrdonnance.SIGNEE);
        if (attribue == null || attribue.isBlank()) {
            throw new RegleDeGestionViolee("ORDONNANCE_NUMERO_REQUIS", "Un numéro est requis pour signer une ordonnance");
        }
        this.statut = StatutOrdonnance.SIGNEE;
        this.numero = attribue;
        this.modifieeLe = maintenant;
        this.signeeLe = maintenant;
    }

    /** L'ordonnance signée a été remise au patient (ce statut n'est atteint qu'une fois). */
    public void marquerImprimee(Instant maintenant) {
        controler(StatutOrdonnance.IMPRIMEE);
        this.statut = StatutOrdonnance.IMPRIMEE;
        this.modifieeLe = maintenant;
    }

    public void annuler(Instant maintenant) {
        controler(StatutOrdonnance.ANNULEE);
        this.statut = StatutOrdonnance.ANNULEE;
        this.modifieeLe = maintenant;
    }

    private void controler(StatutOrdonnance vers) {
        if (!statut.peutPasserA(vers)) {
            throw new RegleDeGestionViolee("ORDONNANCE_TRANSITION_INVALIDE", "Transition de statut invalide : " + statut + " -> " + vers);
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

    public String medecin() {
        return medecin;
    }

    public LocalDate datePrescription() {
        return datePrescription;
    }

    public StatutOrdonnance statut() {
        return statut;
    }

    public String numero() {
        return numero;
    }

    public List<LigneDOrdonnance> lignes() {
        return lignes;
    }

    public Instant creeeLe() {
        return creeeLe;
    }

    public Instant modifieeLe() {
        return modifieeLe;
    }

    public Instant signeeLe() {
        return signeeLe;
    }
}
