package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Prise en charge (PEC, racine) : l'accord d'un payeur pour un patient. Cycle CREE, VALIDEE, CLOTUREE sans retour.
 * <p>
 * REPRODUIT À L'IDENTIQUE, À DÉCIDER AVEC LES MÉTIERS (argent, KB-PAT-24, 25, 26) : aucun contrôle de période (fin avant début,
 * passé), de forfait (existant, du centre) ni de chevauchement ; plusieurs PEC validées simultanées acceptées ; suppression d'une
 * PEC validée possible ; {@link #autoriseSeance()} ne regarde que le statut (la création de séance a sa propre règle, dans le
 * contexte Séance).
 */
public final class PriseEnCharge {

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private LocalDate demandeDebut;
    private LocalDate demandeFin;
    private UUID demandeForfait;
    private LocalDate accordDebut;
    private LocalDate accordFin;
    private UUID accordForfait;
    private StatutPec statut;
    private final Instant creeLe;

    private PriseEnCharge(UUID id, PatientId patient, CentreId centre, LocalDate demandeDebut, LocalDate demandeFin,
                          UUID demandeForfait, LocalDate accordDebut, LocalDate accordFin, UUID accordForfait, StatutPec statut,
                          Instant creeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.demandeDebut = demandeDebut;
        this.demandeFin = demandeFin;
        this.demandeForfait = demandeForfait;
        this.accordDebut = accordDebut;
        this.accordFin = accordFin;
        this.accordForfait = accordForfait;
        this.statut = statut;
        this.creeLe = creeLe;
    }

    /** Nouvelle demande : statut CREE. */
    public static PriseEnCharge demander(UUID id, PatientId patient, CentreId centre, LocalDate debut, LocalDate fin, UUID forfait,
                                         Instant maintenant) {
        return new PriseEnCharge(id, patient, centre, debut, fin, forfait, null, null, null, StatutPec.CREE, maintenant);
    }

    /** Relit une PEC enregistrée. */
    public static PriseEnCharge reconstituer(UUID id, PatientId patient, CentreId centre, LocalDate demandeDebut, LocalDate demandeFin,
                                             UUID demandeForfait, LocalDate accordDebut, LocalDate accordFin, UUID accordForfait,
                                             StatutPec statut, Instant creeLe) {
        return new PriseEnCharge(id, patient, centre, demandeDebut, demandeFin, demandeForfait, accordDebut, accordFin,
                accordForfait, statut, creeLe);
    }

    /**
     * Un patient non vacancier a besoin d'une attestation de droits valide à la date du jour DU CENTRE pour qu'une PEC soit créée
     * (décision du 2026-11-03, KB-PAT-20 : plus la date de la JVM).
     */
    public static void verifierAttestation(boolean vacancier, boolean attestationValide) {
        if (!vacancier && !attestationValide) {
            throw new RegleDePatientViolee("BUSINESS_RULE_VIOLATION", "Attestation valide obligatoire pour PEC non-vacancier");
        }
    }

    /** Valide la PEC (CREE vers VALIDEE) en posant l'accord ; toute autre transition est refusée en 422. */
    public void valider(LocalDate debut, LocalDate fin, UUID forfait) {
        if (statut != StatutPec.CREE) {
            throw new RegleDePatientViolee("BUSINESS_RULE_VIOLATION", "Transition invalide vers VALIDEE");
        }
        this.accordDebut = debut;
        this.accordFin = fin;
        this.accordForfait = forfait;
        this.statut = StatutPec.VALIDEE;
    }

    /** Clôture la PEC (VALIDEE vers CLOTUREE) ; toute autre transition est refusée en 422. */
    public void cloturer() {
        if (statut != StatutPec.VALIDEE) {
            throw new RegleDePatientViolee("BUSINESS_RULE_VIOLATION", "Transition invalide vers CLOTUREE");
        }
        this.statut = StatutPec.CLOTUREE;
    }

    /** Règle « session-allowed » (statut seul) ; l'éligibilité de la séance est une autre règle, à décider avec les métiers. */
    public boolean autoriseSeance() {
        return statut == StatutPec.VALIDEE;
    }

    /**
     * Règle « session-allowed » complète : un non-vacancier sans attestation valide ce jour (du centre) est refusé ; sinon le
     * statut seul décide (reproduit, KB-PAT-25).
     */
    public boolean autoriseSeance(boolean vacancier, boolean attestationValide) {
        if (!vacancier && !attestationValide) return false;
        return autoriseSeance();
    }

    /** Les informations de la demande sont-elles déjà celles-ci ? */
    public boolean demandeIdentique(LocalDate debut, LocalDate fin, UUID forfait) {
        return Objects.equals(demandeDebut, debut) && Objects.equals(demandeFin, fin) && Objects.equals(demandeForfait, forfait);
    }

    /**
     * Révise la demande sans toucher au statut ni à l'accord : enregistrer la fiche ne ramène jamais une PEC validée à « créée ».
     *
     * @return vrai si la demande a changé
     */
    public boolean reviserDemande(LocalDate debut, LocalDate fin, UUID forfait) {
        if (demandeIdentique(debut, fin, forfait)) return false;
        this.demandeDebut = debut;
        this.demandeFin = fin;
        this.demandeForfait = forfait;
        return true;
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

    public LocalDate demandeDebut() {
        return demandeDebut;
    }

    public LocalDate demandeFin() {
        return demandeFin;
    }

    public UUID demandeForfait() {
        return demandeForfait;
    }

    public LocalDate accordDebut() {
        return accordDebut;
    }

    public LocalDate accordFin() {
        return accordFin;
    }

    public UUID accordForfait() {
        return accordForfait;
    }

    public StatutPec statut() {
        return statut;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
