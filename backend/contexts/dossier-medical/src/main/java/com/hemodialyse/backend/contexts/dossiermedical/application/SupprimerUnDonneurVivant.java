package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Supprime un donneur vivant. REPRODUIT (KB-MED-88, à confirmer) : un donneur inexistant, d'un autre patient ou d'un autre centre n'est
 * pas une erreur (204). Écart décidé : le cas d'usage dit si quelque chose a été supprimé (pas d'audit d'une suppression sans effet).
 * Droit : MEDECIN.
 */
public class SupprimerUnDonneurVivant {
    private final SessionCourante session;
    private final DonneursVivants donneurs;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUnDonneurVivant(SessionCourante session, DonneursVivants donneurs, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.donneurs = donneurs;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si un donneur a été supprimé. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> donneurs.supprimer(centre, patient, id));
    }
}
