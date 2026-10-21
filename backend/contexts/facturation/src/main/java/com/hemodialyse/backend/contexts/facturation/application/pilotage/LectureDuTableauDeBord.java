package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.YearMonth;

/**
 * Port sortant de lecture : les chiffres de facturation d'un centre pour un mois. Aucun filtre ni règle : le centre est déjà
 * confronté à la session par l'appelant.
 */
public interface LectureDuTableauDeBord {

    TableauDeBordDuMois du(CentreId centre, YearMonth mois);
}
