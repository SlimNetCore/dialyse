package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

import java.util.Map;

/**
 * Port sortant : les profils de planification des infirmiers d'un centre.
 */
public interface ProfilsRepository {

    /** Les profils enregistrés du centre (lecture de l'optimisation). */
    Map<InfirmierId, ProfilDePlanification> duCentre(CentreId centre);

    /** Remplace le profil de l'infirmier (crée la ligne si besoin). */
    void enregistrer(CentreId centre, ProfilDePlanification profil);
}
