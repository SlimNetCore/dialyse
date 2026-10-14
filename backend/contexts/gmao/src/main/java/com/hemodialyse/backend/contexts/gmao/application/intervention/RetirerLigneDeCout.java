package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;

import java.time.Instant;

/** Retire une ligne de coût d'une intervention ouverte (R17) ; une intervention terminée se rectifie d'abord. */
public final class RetirerLigneDeCout {

    private final InterventionRepository interventions;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public RetirerLigneDeCout(InterventionRepository interventions, Horloge horloge, UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(InterventionId id, CentreId centre, LigneDeCoutId ligne, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(id).filter(i -> i.centreId().equals(centre))
                    .orElseThrow(InterventionIntrouvable::new);
            Instant maintenant = horloge.maintenant();
            intervention.retirerLigneDeCout(ligne, par, maintenant);
            interventions.enregistrer(intervention);
            return InterventionVue.depuis(intervention, maintenant);
        });
    }
}
