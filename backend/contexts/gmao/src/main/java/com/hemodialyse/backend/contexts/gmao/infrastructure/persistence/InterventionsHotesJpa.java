package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;

import java.util.Optional;

/**
 * Adapter JPA du port {@link InterventionsHotes} : lit la table des interventions (même contexte) sans passer par l'agrégat.
 * Une intervention supprimée logiquement ({@code deleted_at}) reste trouvable, marquée supprimée. {@link #verrouiller} pose un
 * verrou d'écriture sur la ligne ({@code SELECT ... FOR UPDATE}) qui dure jusqu'à la fin de la transaction du cas d'usage.
 */
public class InterventionsHotesJpa implements InterventionsHotes {

    private final InterventionSpringData donnees;

    public InterventionsHotesJpa(InterventionSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public Optional<InterventionHote> trouver(InterventionId id) {
        return donnees.findById(id.valeur()).map(InterventionsHotesJpa::hote);
    }

    @Override
    public Optional<InterventionHote> verrouiller(InterventionId id) {
        return donnees.verrouiller(id.valeur()).map(InterventionsHotesJpa::hote);
    }

    private static InterventionHote hote(InterventionJpa e) {
        return new InterventionHote(new InterventionId(e.id), new CentreId(e.centreId), StatutIntervention.valueOf(e.statut),
                e.deletedAt != null);
    }
}
