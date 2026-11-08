package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/** Port sortant : les centres de la solution (tenus par l'Organisation, hors contexte), parcourus par le rappel quotidien. */
public interface CentresARappeler {
    List<CentreId> tous();
}
