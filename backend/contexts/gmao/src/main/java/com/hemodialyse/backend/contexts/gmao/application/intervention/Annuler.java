package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;

import java.time.Instant;

/**
 * Annule une intervention (R10). L'équipement n'est pas touché : annuler une intervention en cours le laisse dans l'état
 * posé au démarrage (KB-GMAO-48, reproduit) ; annuler une intervention déjà annulée est accepté (KB-GMAO-02).
 */
public final class Annuler {

    private final InterventionRepository interventions;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public Annuler(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                   UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(InterventionId id, CentreId centre, String raison, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(id).filter(i -> i.centreId().equals(centre))
                    .orElseThrow(InterventionIntrouvable::new);
            Instant maintenant = horloge.maintenant();
            intervention.annuler(raison, par, maintenant);
            interventions.enregistrer(intervention);
            publicateur.publier(intervention.tirerEvenements());
            return InterventionVue.depuis(intervention, maintenant);
        });
    }
}
