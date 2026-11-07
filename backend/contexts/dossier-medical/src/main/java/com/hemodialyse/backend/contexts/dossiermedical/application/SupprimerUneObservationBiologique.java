package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Supprime une observation du patient (une observation dérivée d'un bilan reste supprimable : reproduit, KB-MED-84). Reproduit (KB-MED-75,
 * à confirmer avec les métiers) : une observation inexistante, d'un autre patient ou d'un autre centre n'est pas une erreur (204). Écart
 * décidé : le cas d'usage dit si quelque chose a été supprimé, pour que l'interface n'inscrive plus au journal d'audit une suppression qui
 * n'a pas eu lieu. Droit : MEDECIN.
 */
public class SupprimerUneObservationBiologique {
    private final SessionCourante session;
    private final ObservationsBiologiques observations;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUneObservationBiologique(SessionCourante session, ObservationsBiologiques observations, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.observations = observations;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si une observation a été supprimée. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> observations.trouverEnVerrouillant(centre, patient, id)
                .map(trouvee -> observations.supprimer(centre, patient, id)).orElse(false));
    }
}
