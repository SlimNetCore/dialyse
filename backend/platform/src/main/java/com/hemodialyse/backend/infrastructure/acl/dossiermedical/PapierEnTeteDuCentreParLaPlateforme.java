package com.hemodialyse.backend.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PapierEnTeteDuCentre;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ACL du Dossier médical vers le papier en-tête du contexte Documents (documents.v1, DOC1, ADR 0142) : l'en-tête et le pied de page
 * d'identité du centre et de sa société sont posés par {@code documents.v1.PapierEnTeteDuCentre#habiller}, comme pour tous les documents
 * HTML rendus en PDF. Le port du Dossier médical est gardé tel quel : il est l'ACL du contexte consommateur (le Dossier médical ne dépend
 * pas de Documents).
 */
@Component
public class PapierEnTeteDuCentreParLaPlateforme implements PapierEnTeteDuCentre {

    private final com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre papier;

    public PapierEnTeteDuCentreParLaPlateforme(com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre papier) {
        this.papier = papier;
    }

    @Override
    public String habiller(String html, UUID centre) {
        return papier.habiller(html, centre);
    }
}
