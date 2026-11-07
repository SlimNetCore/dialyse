package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Supprime un antécédent du patient. Reproduit (KB-MED-60, à confirmer avec les métiers) : un antécédent inexistant, d'un autre patient
 * ou d'un autre centre n'est pas une erreur (204). Écart décidé : le cas d'usage dit si quelque chose a été supprimé, pour que
 * l'interface n'inscrive plus au journal d'audit une suppression qui n'a pas eu lieu. Droit : MEDECIN.
 */
public class SupprimerUnAntecedent {
    private final SessionCourante session;
    private final Antecedents antecedents;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUnAntecedent(SessionCourante session, Antecedents antecedents, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.antecedents = antecedents;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si un antécédent a été supprimé. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> antecedents.trouverEnVerrouillant(centre, patient, id)
                .map(trouve -> antecedents.supprimer(centre, patient, id)).orElse(false));
    }
}
