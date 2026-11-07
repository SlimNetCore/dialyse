package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/** Port sortant : les centres de la solution (tenus par l'Organisation, hors contexte), pour les traitements de nuit. */
public interface CentresActifs {
    List<CentreId> tous();
}
