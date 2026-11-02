package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant (lecture seule) : une salle du centre avec sa capacité et ses générateurs affectés (mêmes règles que
 * {@link VueDesSalles}). Vide pour une salle inconnue ou d'un autre centre.
 */
public interface SalleDuCentre {

    Optional<VueDesSalles.SalleVue> parIdentifiant(CentreId centre, UUID salle);
}
