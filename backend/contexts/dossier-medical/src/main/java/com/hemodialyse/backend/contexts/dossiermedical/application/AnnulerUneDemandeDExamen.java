package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/** Annule une demande d'examen, tant que le résultat n'est pas disponible (DEMANDE ou PRELEVE vers ANNULE). Droit : MEDECIN. */
public class AnnulerUneDemandeDExamen {

    private final SessionCourante session;
    private final DemandesDExamen demandes;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public AnnulerUneDemandeDExamen(SessionCourante session, DemandesDExamen demandes, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.demandes = demandes;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public DemandeExamen executer(UUID centreDemande, UUID patient, UUID id) {
        var centre = session.centre(centreDemande);
        return uniteDeTravail.executer(() -> {
            DemandeExamen demande = demandes.trouverEnVerrouillant(centre, new PatientId(patient), id).orElseThrow(DemandeExamenIntrouvable::new);
            demande.annuler(horloge.maintenant());
            demandes.enregistrerLaTransition(demande);
            return demande;
        });
    }
}
