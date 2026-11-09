package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;

import java.util.UUID;

/** Le faux en mémoire passe le contrat des libellés. */
class LibellesEnMemoireContratTest extends LibellesContrat {

    private final LibellesEnMemoire memoire = new LibellesEnMemoire();

    @Override
    protected LibellesDeLOrganisation libelles() {
        return memoire;
    }

    @Override
    protected void salle(UUID centre, UUID id, String code, String nom) {
        memoire.salle(centre, id, code, nom);
    }

    @Override
    protected void creneau(UUID centre, UUID id, String code, String libelle) {
        memoire.creneau(centre, id, code, libelle);
    }

    @Override
    protected void medecin(UUID centre, UUID id, String nom, String prenom, boolean actif) {
        memoire.medecin(centre, id, nom, prenom);
    }
}
