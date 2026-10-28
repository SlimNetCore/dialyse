package com.hemodialyse.backend.contexts.planification.contrat;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.doubles.ParametrageEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;

/**
 * Contrat du paramétrage exécuté contre les doubles en mémoire.
 */
class ParametrageEnMemoireContratTest extends ParametrageContrat {

    private final ParametrageEnMemoire monde = new ParametrageEnMemoire();

    @Override
    protected ParametresDuPlanningRepository parametres() {
        return monde.parametres;
    }

    @Override
    protected ReglagesRepository reglages() {
        return monde.reglages;
    }

    @Override
    protected PreferencesRepository preferences() {
        return monde.preferences;
    }

    @Override
    protected ProfilsRepository profils() {
        return monde.profils;
    }

    @Override
    protected void salle(CentreId centre, SalleId salle, boolean isolement) {
        monde.salles.ajouter(centre, salle, isolement);
    }
}
