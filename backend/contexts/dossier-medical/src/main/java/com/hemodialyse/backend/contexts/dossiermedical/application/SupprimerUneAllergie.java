package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Supprime une allergie du patient. Reproduit (KB-MED-60, à confirmer avec les métiers) : une allergie inexistante, d'un autre patient ou
 * d'un autre centre n'est pas une erreur (204). Écart décidé : le cas d'usage dit si quelque chose a été supprimé, pour que l'interface
 * n'inscrive plus au journal d'audit une suppression qui n'a pas eu lieu. Droit : MEDECIN.
 */
public class SupprimerUneAllergie {
    private final SessionCourante session;
    private final Allergies allergies;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUneAllergie(SessionCourante session, Allergies allergies, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.allergies = allergies;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si une allergie a été supprimée. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> allergies.trouverEnVerrouillant(centre, patient, id)
                .map(trouvee -> allergies.supprimer(centre, patient, id)).orElse(false));
    }
}
