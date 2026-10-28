package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;

import java.time.LocalDate;

/**
 * Port sortant : le taux de TVA des prestations d'hémodialyse du centre à une date. Les règles de TVA appartiennent à la
 * Comptabilité (ADR 0030) ; l'adapter lit son langage publié.
 */
public interface TauxDeTvaApplicable {

    /**
     * Le taux de la règle en vigueur à cette date ; {@link TauxDeTva#AUCUN} s'il n'y en a pas.
     */
    TauxDeTva aLaDate(CentreId centre, LocalDate date);
}
