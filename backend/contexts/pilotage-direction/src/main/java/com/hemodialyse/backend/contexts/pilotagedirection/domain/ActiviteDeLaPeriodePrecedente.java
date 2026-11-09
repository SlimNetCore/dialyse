package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/**
 * Les séances et les finances de la période de même durée qui précède celle qu'on affiche, pour les écarts. Le nombre de patients n'y
 * figure volontairement pas : c'est un effectif constaté, pas une donnée d'activité mesurée sur la période.
 */
public record ActiviteDeLaPeriodePrecedente(Periode periode, long seances, ChiffresFinanciers finances) {
}
