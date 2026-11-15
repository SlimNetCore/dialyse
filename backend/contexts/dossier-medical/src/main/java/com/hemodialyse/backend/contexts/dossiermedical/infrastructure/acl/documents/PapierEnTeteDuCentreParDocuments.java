package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PapierEnTeteDuCentre;

import java.util.UUID;

/**
 * ACL du Dossier médical vers le papier en-tête du contexte Documents (documents.v1, DOC1, ADR 0142) : l'en-tête et le pied de page
 * d'identité du centre et de sa société sont posés par {@code documents.v1.PapierEnTeteDuCentre#habiller}, comme pour tous les documents
 * HTML rendus en PDF. Le port du Dossier médical est gardé tel quel : il est l'ACL du contexte consommateur. Hébergée par le Dossier
 * médical (ADR 0192) : Documents ne dépend que de l'Organisation et de documents-api, aucun cycle Maven.
 */
public class PapierEnTeteDuCentreParDocuments implements PapierEnTeteDuCentre {

    private final com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre papier;

    public PapierEnTeteDuCentreParDocuments(com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre papier) {
        this.papier = papier;
    }

    @Override
    public String habiller(String html, UUID centre) {
        return papier.habiller(html, centre);
    }
}
