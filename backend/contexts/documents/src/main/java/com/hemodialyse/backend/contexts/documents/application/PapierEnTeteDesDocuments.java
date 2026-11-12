package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.PapierEnTete;
import com.hemodialyse.backend.contexts.documents.domain.PapierEnTete.EnTeteDeSociete;
import com.hemodialyse.backend.contexts.documents.domain.ParametresReserves;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Cas d'usage : composer le papier en-tête d'un centre ou d'une société à partir de l'identité imprimée que fournit Organisation. */
public class PapierEnTeteDesDocuments {

    private final IdentitesImprimables identites;

    public PapierEnTeteDesDocuments(IdentitesImprimables identites) {
        this.identites = identites;
    }

    /** Les paramètres de gabarit d'identité du centre ; jamais nul. */
    public Map<String, Object> parametres(UUID centre) {
        return PapierEnTete.parametres(identites.duCentre(centre).orElse(null));
    }

    /** Le document HTML habillé de l'en-tête et du pied de page du centre. */
    public String habiller(String html, UUID centre) {
        return PapierEnTete.habiller(html, parametres(centre));
    }

    public Optional<EnTeteDeSociete> enTeteDeLaSociete(UUID societe) {
        return identites.deLaSociete(societe).flatMap(s -> PapierEnTete.enTeteDeLaSociete(s));
    }

    public Map<String, Object> ecarterLIdentite(Map<String, Object> parametres) {
        return ParametresReserves.ecarterLIdentite(parametres);
    }
}
