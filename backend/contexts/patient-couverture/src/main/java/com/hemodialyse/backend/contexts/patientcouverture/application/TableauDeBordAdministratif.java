package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.time.YearMonth;

/** Les compteurs du tableau de bord administratif d'un centre ; {@code mois} nul : pas de filtre. */
public record TableauDeBordAdministratif(long pecCree, long pecValidee, long pecExpiring, long attestationTotal, long attestationExpiring,
                            long patientCount, int expirationDays, YearMonth mois) {
}
