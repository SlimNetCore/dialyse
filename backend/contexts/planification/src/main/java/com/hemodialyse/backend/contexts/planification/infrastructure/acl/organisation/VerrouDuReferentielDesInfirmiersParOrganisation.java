package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.VerrouDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RefusDeVerrou;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Verrou du référentiel des infirmiers (ADR 0056) : la ligne du centre, qui appartient à Organisation, est réservée par son langage
 * publié {@link VerrouDuCentre#reserverLeCentre} (O4 lot D, ADR 0110) : un verrou {@code FOR NO KEY UPDATE} sur PostgreSQL, qui exclut
 * les autres écritures du référentiel du même centre sans bloquer les insertions des tables qui référencent le centre. Le refus de
 * verrou (délai, interblocage) devient une écriture concurrente, traduite ICI : c'est la sémantique de la Planification. Un centre sans
 * ligne n'a rien à verrouiller : la contrainte d'unicité reste le dernier garde-fou.
 */
public class VerrouDuReferentielDesInfirmiersParOrganisation implements VerrouDuReferentielDesInfirmiers {

    private final VerrouDuCentre verrou;

    public VerrouDuReferentielDesInfirmiersParOrganisation(VerrouDuCentre verrou) {
        this.verrou = verrou;
    }

    @Override
    public void prendre(CentreId centre) {
        RefusDeVerrou.verrouillant(() -> {
            verrou.reserverLeCentre(centre.valeur());
            return null;
        });
    }
}
