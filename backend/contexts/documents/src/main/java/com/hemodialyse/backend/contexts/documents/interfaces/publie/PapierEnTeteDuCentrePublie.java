package com.hemodialyse.backend.contexts.documents.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.application.PapierEnTeteDesDocuments;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Adapter entrant du langage publié {@link PapierEnTeteDuCentre} : délègue au cas d'usage et traduit l'en-tête de société. */
public class PapierEnTeteDuCentrePublie implements PapierEnTeteDuCentre {

    private final PapierEnTeteDesDocuments papier;

    public PapierEnTeteDuCentrePublie(PapierEnTeteDesDocuments papier) {
        this.papier = papier;
    }

    @Override
    public String habiller(String html, UUID centre) {
        return papier.habiller(html, centre);
    }

    @Override
    public Map<String, Object> parametresDeLEnTete(UUID centre) {
        return papier.parametres(centre);
    }

    @Override
    public Map<String, Object> ecarterLIdentiteDeLAppelant(Map<String, Object> parametres) {
        return papier.ecarterLIdentite(parametres);
    }

    @Override
    public Optional<EnTeteDeSociete> enTeteDeLaSociete(UUID societe) {
        return papier.enTeteDeLaSociete(societe).map(e -> new EnTeteDeSociete(e.nom(), e.contact(), e.legal(), e.piedDePage(), e.logo()));
    }
}
