package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheId;

import java.time.Instant;

/** Démarre une tâche d'une intervention en cours (R21) ; publie {@code TacheDemarree}. Fonctionnalité nouvelle (KB-GMAO-55). */
public final class DemarrerTache {

    private final InterventionRepository interventions;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public DemarrerTache(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                     UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(InterventionId id, CentreId centre, TacheId tache, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(id).filter(i -> i.centreId().equals(centre))
                    .orElseThrow(InterventionIntrouvable::new);
            Instant maintenant = horloge.maintenant();
            intervention.demarrerTache(tache, par, maintenant);
            interventions.enregistrer(intervention);
            publicateur.publier(intervention.tirerEvenements());
            return InterventionVue.depuis(intervention, maintenant);
        });
    }
}
