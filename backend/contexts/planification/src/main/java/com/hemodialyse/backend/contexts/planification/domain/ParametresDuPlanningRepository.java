package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;

/**
 * Port sortant : le paramétrage du planning d'un centre (un par centre, toujours borné au centre).
 */
public interface ParametresDuPlanningRepository {

    /** Le paramétrage du centre, ou le paramétrage par défaut tant qu'il n'en a pas enregistré. Ne crée rien. */
    ParametresDuPlanning lire(CentreId centre);

    /** Remplace le paramétrage du centre (crée la ligne si besoin) et ses salles d'isolement. */
    void enregistrer(CentreId centre, ParametresDuPlanning parametres);
}
