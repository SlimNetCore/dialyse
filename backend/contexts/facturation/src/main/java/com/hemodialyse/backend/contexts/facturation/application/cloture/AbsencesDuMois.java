package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.YearMonth;

/**
 * Port sortant : les absences du mois sont-elles closes ? (avertissement de la clôture, jamais bloquant). Adapter : ACL vers le
 * langage publié du module Absences.
 */
public interface AbsencesDuMois {

    boolean sontCloses(CentreId centre, YearMonth mois);
}
