package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import java.time.Clock;

/**
 * Les instants du Pilotage, injectés dans sa composition (KB-DIR-03, lot L6) : un bean de ce type remplace l'horloge système en UTC, ce
 * qui permet aux tests d'intégration de FIGER l'heure. Un type dédié, et non une {@link Clock} nue : d'autres contextes injectent une
 * {@code Clock} par type et ne doivent pas voir celle-ci.
 */
public record InstantsDuPilotage(Clock horloge) {
}
