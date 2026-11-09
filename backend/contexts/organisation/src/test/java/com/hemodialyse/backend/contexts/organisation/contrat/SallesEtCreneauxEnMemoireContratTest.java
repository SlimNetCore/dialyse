package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DefinirLesSallesDIsolement;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;

import java.util.UUID;

/** Le faux en mémoire passe le contrat des salles et des créneaux. */
class SallesEtCreneauxEnMemoireContratTest extends SallesEtCreneauxContrat {

    private final SallesEtCreneauxEnMemoire memoire = new SallesEtCreneauxEnMemoire();

    @Override
    protected SallesEtCreneauxDuCentre lectures() {
        return memoire;
    }

    @Override
    protected DefinirLesSallesDIsolement commande() {
        return memoire;
    }

    @Override
    protected void salle(UUID centre, UUID id, String code, String nom, boolean isolement, Integer capacite) {
        memoire.salle(centre, id, code, nom, isolement, capacite);
    }

    @Override
    protected void creneau(UUID centre, UUID id, String code, String libelle) {
        memoire.creneau(centre, id, code, libelle);
    }
}
