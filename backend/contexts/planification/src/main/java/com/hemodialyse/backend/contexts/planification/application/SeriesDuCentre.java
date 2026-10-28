package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


/** Port sortant vers Organisation : le nombre de séries (créneaux) par jour du centre, base de sa capacité théorique. */
public interface SeriesDuCentre {

    int nombre(CentreId centre);
}
