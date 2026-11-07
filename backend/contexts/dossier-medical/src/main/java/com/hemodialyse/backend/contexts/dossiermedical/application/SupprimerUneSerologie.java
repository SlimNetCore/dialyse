package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;

import java.util.UUID;

/**
 * Supprime une sérologie du patient du centre de la session. Reproduit (KB-MED-49, à confirmer avec les métiers) : une sérologie
 * inexistante, d'un autre patient ou d'un autre centre n'est pas une erreur (la route répond 204). Écart décidé : le cas d'usage dit
 * si quelque chose a été supprimé, pour que l'interface n'inscrive plus au journal d'audit une suppression qui n'a pas eu lieu. La
 * Planification n'est pas appelée (comme le legacy : la place du patient n'est pas réévaluée par une suppression). Droit : MEDECIN.
 */
public class SupprimerUneSerologie {

    private final SessionCourante session;
    private final Serologies serologies;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUneSerologie(SessionCourante session, Serologies serologies, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.serologies = serologies;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si une sérologie a été supprimée. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> serologies.trouverEnVerrouillant(centre, patient, id)
                .map(trouvee -> serologies.supprimer(centre, patient, id)).orElse(false));
    }
}
