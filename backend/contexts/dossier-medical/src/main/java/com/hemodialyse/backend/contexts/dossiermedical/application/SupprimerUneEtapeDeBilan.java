package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Supprime une étape. REPRODUIT (KB-MED-88, à confirmer) : une étape inexistante, d'un autre patient ou d'un autre centre n'est pas une
 * erreur (204). Écart décidé : le cas d'usage dit si quelque chose a été supprimé, pour que l'interface n'inscrive plus au journal
 * d'audit une suppression qui n'a pas eu lieu. Droit : MEDECIN seul.
 */
public class SupprimerUneEtapeDeBilan {
    private final SessionCourante session;
    private final EtapesDeBilan etapes;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUneEtapeDeBilan(SessionCourante session, EtapesDeBilan etapes, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.etapes = etapes;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si une étape a été supprimée. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> etapes.supprimer(centre, patient, id));
    }
}
