package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Port sortant : le rendu du cahier de dialyse, confié à Documents (documents.v1) avec le modèle actif du centre. */
public interface RenduDuCahier {

    CahierImprime rendre(CentreId centre, DemandeDeCahier demande);
}
