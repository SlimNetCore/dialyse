package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Associe à une prescription les articles de son catalogue (lecture). Partagé par les cas d'usage de consultation et de saisie. */
class DetaillerLesPrescriptions {

    private final CatalogueDesArticles catalogue;

    DetaillerLesPrescriptions(CatalogueDesArticles catalogue) {
        this.catalogue = catalogue;
    }

    PrescriptionDetaillee de(CentreId centre, PrescriptionMedicale p) {
        var epo = p.contenu().epo().article() == null ? java.util.Optional.<CatalogueDesArticles.ArticleDeTraitement>empty()
                : catalogue.article(centre, p.contenu().epo().article());
        var fer = p.contenu().fer().article() == null ? java.util.Optional.<CatalogueDesArticles.ArticleDeTraitement>empty()
                : catalogue.article(centre, p.contenu().fer().article());
        return new PrescriptionDetaillee(p, epo, fer);
    }
}
