package com.hemodialyse.backend.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ACL du Dossier médical vers le moteur de documents de la plateforme (PL2) : l'en-tête et le pied de page d'identité du centre et de sa
 * société sont posés par {@link DocumentIdentityProvider#decorateHtml}, comme pour tous les documents HTML rendus en PDF.
 */
@Component
public class PapierEnTeteDuCentreParLaPlateforme implements PapierEnTeteDuCentre {

    private final DocumentIdentityProvider identite;

    public PapierEnTeteDuCentreParLaPlateforme(DocumentIdentityProvider identite) {
        this.identite = identite;
    }

    @Override
    public String habiller(String html, UUID centre) {
        return identite.decorateHtml(html, centre);
    }
}
