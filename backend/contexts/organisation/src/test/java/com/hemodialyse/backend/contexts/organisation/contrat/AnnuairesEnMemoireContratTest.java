package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;

import java.util.UUID;

/** Le faux en mémoire passe le contrat des annuaires. */
class AnnuairesEnMemoireContratTest extends AnnuairesContrat {

    private final AnnuairesEnMemoire memoire = new AnnuairesEnMemoire();

    @Override
    protected AnnuaireDesCentres centres() {
        return memoire;
    }

    @Override
    protected AnnuaireDesSocietes societes() {
        return memoire;
    }

    @Override
    protected void societe(UUID id, String raisonSociale, boolean actif) {
        memoire.societe(id, raisonSociale, actif);
    }

    @Override
    protected void centre(UUID id, UUID societe, String nom, Boolean actif, String fuseau) {
        memoire.centre(id, societe, nom, actif, fuseau);
    }
}
