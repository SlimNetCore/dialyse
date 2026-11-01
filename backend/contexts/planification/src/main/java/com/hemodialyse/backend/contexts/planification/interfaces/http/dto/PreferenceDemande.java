package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import java.util.UUID;

/**
 * @param joursAChoisir obligatoire : les jours de dialyse sont choisis par l'optimisation ({@code seancesParSemaine} requis)
 */
public record PreferenceDemande(UUID creneauPrefereId, Integer seancesParSemaine, Boolean joursAChoisir) {
}
