package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.patientcouverture.application.VerrouDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * ACL du port {@link VerrouDuCentre} vers le langage publié {@link VerrouDesPlacesDuCentre} de la Planification : le même verrou
 * que celui du contrôle de place (ligne de paramètres du centre, créée si elle manque), pris ici même quand la fiche n'a pas de
 * place. Un verrou non obtenu dans le délai traverse tel quel ({@code PlacementDesPatients.EcritureConcurrente}, 409).
 */
public class VerrouDuCentreParLaPlanification implements VerrouDuCentre {

    private final VerrouDesPlacesDuCentre verrou;

    public VerrouDuCentreParLaPlanification(VerrouDesPlacesDuCentre verrou) {
        this.verrou = verrou;
    }

    @Override
    public void prendre(CentreId centre) {
        verrou.verrouiller(centre.valeur());
    }
}
