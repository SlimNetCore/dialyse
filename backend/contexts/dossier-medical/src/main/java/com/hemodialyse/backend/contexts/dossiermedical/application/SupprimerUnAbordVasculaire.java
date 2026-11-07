package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Supprime un abord vasculaire du patient. Reproduit (KB-MED-60, à confirmer avec les métiers) : un abord inexistant, d'un autre patient ou
 * d'un autre centre n'est pas une erreur (204). Écart décidé : le cas d'usage dit si quelque chose a été supprimé, pour que l'interface
 * n'inscrive plus au journal d'audit une suppression qui n'a pas eu lieu. Droit : MEDECIN.
 */
public class SupprimerUnAbordVasculaire {
    private final SessionCourante session;
    private final AbordsVasculaires abords;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUnAbordVasculaire(SessionCourante session, AbordsVasculaires abords, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.abords = abords;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si un abord a été supprimé. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> abords.trouverEnVerrouillant(centre, patient, id)
                .map(trouve -> abords.supprimer(centre, patient, id)).orElse(false));
    }
}
