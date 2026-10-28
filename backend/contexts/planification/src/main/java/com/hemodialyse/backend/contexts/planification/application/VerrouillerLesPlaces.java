package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre;

import java.util.UUID;

/**
 * Prend, pour la transaction de l'appelant (l'application d'une proposition d'optimisation, encore dans le legacy), le verrou des
 * places du centre : la fiche, la sérologie et l'optimisation s'excluent mutuellement.
 */
public class VerrouillerLesPlaces implements VerrouDesPlacesDuCentre {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDesPlaces verrou;

    public VerrouillerLesPlaces(UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
    }

    @Override
    public void verrouiller(UUID centre) {
        RefusDuPlacement.traduire(() -> uniteDeTravail.executer(() -> {
            verrou.prendre(new CentreId(centre));
            return null;
        }));
    }
}
