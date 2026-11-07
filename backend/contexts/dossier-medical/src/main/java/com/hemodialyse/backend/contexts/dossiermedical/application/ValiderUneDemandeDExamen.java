package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Le médecin valide les résultats et clôt la demande (RESULTAT_DISPONIBLE vers VALIDE). La conclusion est facultative et non contrôlée
 * (reproduit, KB-MED-42, à confirmer avec les métiers). Droit : MEDECIN.
 */
public class ValiderUneDemandeDExamen {

    private final SessionCourante session;
    private final DemandesDExamen demandes;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public ValiderUneDemandeDExamen(SessionCourante session, DemandesDExamen demandes, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.demandes = demandes;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public DemandeExamen executer(UUID centreDemande, UUID patient, UUID id, String conclusion) {
        var centre = session.centre(centreDemande);
        return uniteDeTravail.executer(() -> {
            DemandeExamen demande = demandes.trouverEnVerrouillant(centre, new PatientId(patient), id).orElseThrow(DemandeExamenIntrouvable::new);
            demande.valider(conclusion, horloge.maintenant());
            demandes.enregistrerLaTransition(demande);
            return demande;
        });
    }
}
