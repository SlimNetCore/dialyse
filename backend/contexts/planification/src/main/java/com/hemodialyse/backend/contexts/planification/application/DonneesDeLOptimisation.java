package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;

import java.time.LocalDate;

/**
 * Port sortant : lit en une fois l'état du centre soumis à l'optimisation (ressources, personnel, patients actifs et leur place,
 * préférences, profils, indisponibilités de générateurs, déplacements temporaires, séances réalisées de la période). Borné au
 * centre ; lecture seule. Le cas d'usage l'appelle sous le verrou du centre quand l'état doit être stable (lancement, application).
 */
public interface DonneesDeLOptimisation {

    /** @param du premier jour de l'horizon (inclus) @param au dernier jour de l'horizon (inclus) */
    DonneesDOptimisation charger(CentreId centre, LocalDate du, LocalDate au);
}
