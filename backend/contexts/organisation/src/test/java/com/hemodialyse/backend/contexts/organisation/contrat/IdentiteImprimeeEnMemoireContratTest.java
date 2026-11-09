package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;

import java.util.UUID;

/** Le faux en mémoire passe le contrat de l'identité imprimée. */
class IdentiteImprimeeEnMemoireContratTest extends IdentiteImprimeeContrat {

    private final IdentiteImprimeeEnMemoire memoire = new IdentiteImprimeeEnMemoire();

    @Override
    protected IdentiteImprimee identite() {
        return memoire;
    }

    @Override
    protected void societe(UUID id, boolean actif, Fiche fiche) {
        memoire.societe(id, fiche);
    }

    @Override
    protected void centre(UUID id, UUID societe, String nom, String adresse, String ville, String wilaya, String telephone,
                          String email, String siteWeb, boolean actif) {
        memoire.centre(id, societe, nom, adresse, ville, wilaya, telephone, email, siteWeb);
    }
}
