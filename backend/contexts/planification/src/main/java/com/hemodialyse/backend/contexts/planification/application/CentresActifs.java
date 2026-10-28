package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


import java.util.List;

/**
 * Port sortant vers Identité et accès (encore legacy) : les centres actifs, par identifiant (ordre stable). Un centre désactivé n'en
 * fait pas partie ; un centre actif sans infirmier en fait partie (KB-PLN-45).
 */
public interface CentresActifs {

    List<CentreId> liste();
}
