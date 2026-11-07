package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Supprime une analyse du patient. Reproduit (KB-MED-75, à confirmer avec les métiers) : une analyse inexistante, d'un autre patient ou
 * d'un autre centre n'est pas une erreur (204). Écart décidé : le cas d'usage dit si quelque chose a été supprimé, pour que l'interface
 * n'inscrive plus au journal d'audit une suppression qui n'a pas eu lieu. Droit : MEDECIN.
 */
public class SupprimerUneAnalyse {
    private final SessionCourante session;
    private final Analyses analyses;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUneAnalyse(SessionCourante session, Analyses analyses, UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.analyses = analyses;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** Vrai si une analyse a été supprimée. */
    public boolean executer(UUID centreDemande, UUID patientDemande, UUID id) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> analyses.trouverEnVerrouillant(centre, patient, id)
                .map(trouvee -> analyses.supprimer(centre, patient, id)).orElse(false));
    }
}
