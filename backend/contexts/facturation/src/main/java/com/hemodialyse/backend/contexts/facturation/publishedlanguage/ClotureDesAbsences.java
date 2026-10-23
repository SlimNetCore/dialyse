package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Langage publié (besoin de la Facturation, fourni par le module Absences, hors contexte) : le mois d'absences d'un centre est-il
 * clos ? Sert seulement à avertir, à la clôture d'un mois de facturation, que les absences du mois ne sont pas closes (ADR 0009 :
 * l'ordre absences puis facturation reste une recommandation, rien n'est bloqué, ADR 0042).
 */
public interface ClotureDesAbsences {

    boolean moisClos(UUID centre, YearMonth mois);
}
