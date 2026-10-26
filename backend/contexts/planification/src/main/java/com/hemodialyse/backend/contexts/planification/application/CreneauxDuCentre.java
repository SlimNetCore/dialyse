package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;

/**
 * Port sortant vers Organisation : les créneaux d'un centre.
 */
public interface CreneauxDuCentre {

    boolean contient(CentreId centre, CreneauId creneau);
}
