package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs;
import com.hemodialyse.backend.contexts.planification.doubles.InfirmiersEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;

/**
 * Contrat du référentiel des infirmiers exécuté contre le double en mémoire.
 */
class InfirmiersEnMemoireContratTest extends InfirmiersContrat {

    private final InfirmiersEnMemoire monde = new InfirmiersEnMemoire();

    @Override
    protected InfirmiersRepository infirmiers() {
        return monde.infirmiers;
    }

    @Override
    protected ComptesUtilisateurs comptes() {
        return monde.comptesUtilisateurs;
    }

    @Override
    protected CompteId unCompte(String identifiant, String nomComplet, boolean actif, CentreId... centres) {
        return monde.compte(identifiant, nomComplet, actif, centres);
    }
}
