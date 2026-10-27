package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;

/** Port sortant vers Organisation : le nombre de séries (créneaux) par jour du centre, base de sa capacité théorique. */
public interface SeriesDuCentre {

    int nombre(CentreId centre);
}
