package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Le médecin déclare le résultat disponible (PRELEVE vers RESULTAT_DISPONIBLE). Action MANUELLE : la saisie d'une observation liée à
 * la demande ne change jamais le statut, et aucun événement {@code ResultatExamenRecu} n'est publié (reproduit, KB-MED-37, à confirmer
 * avec les métiers). Droit : MEDECIN.
 */
public class RendreLeResultatDisponible {

    private final SessionCourante session;
    private final DemandesDExamen demandes;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public RendreLeResultatDisponible(SessionCourante session, DemandesDExamen demandes, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.demandes = demandes;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public DemandeExamen executer(UUID centreDemande, UUID patient, UUID id) {
        var centre = session.centre(centreDemande);
        return uniteDeTravail.executer(() -> {
            DemandeExamen demande = demandes.trouverEnVerrouillant(centre, new PatientId(patient), id).orElseThrow(DemandeExamenIntrouvable::new);
            demande.rendreLeResultatDisponible(horloge.maintenant());
            demandes.enregistrerLaTransition(demande);
            return demande;
        });
    }
}
