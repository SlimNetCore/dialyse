package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;

/**
 * Lit le paramétrage du planning du centre (RG-PLN-005), ou le paramétrage par défaut ; ne crée rien.
 */
public class ConsulterLesParametres {

    private final ParametresDuPlanningRepository parametres;

    public ConsulterLesParametres(ParametresDuPlanningRepository parametres) {
        this.parametres = parametres;
    }

    public ParametresDuPlanning executer(CentreId centre) {
        return parametres.lire(centre);
    }
}
