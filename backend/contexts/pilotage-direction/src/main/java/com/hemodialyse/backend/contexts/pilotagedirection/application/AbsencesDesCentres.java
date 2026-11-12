package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant (PL10b2/PL10c, ADR 0152) : le cumul des absences de patients des centres (par statut et motif, hors annulées) et leur suivi
 * mensuel, lus dans le langage publié des Absences de patients (absencespatients.v1). Agrégats uniquement ; lecture seule, sans SQL.
 */
public interface AbsencesDesCentres {

    /** Le cumul par centre, statut et motif des absences de séances comprises entre {@code du} et {@code au} inclus. */
    List<CumulDuCentre> cumulParCentre(Collection<UUID> centres, LocalDate du, LocalDate au);

    /** Le nombre et la valorisation HT des absences non rattrapées, par mois. */
    List<AbsencesDuMois> mensuel(Collection<UUID> centres, LocalDate du, LocalDate au);

    /** {@code statut} : JUSTIFIEE, NON_JUSTIFIEE, A_QUALIFIER ou RATTRAPEE ; {@code motif} nul tant que l'absence n'est pas qualifiée. */
    record CumulDuCentre(UUID centre, String statut, String motif, long nombre, BigDecimal montantHt, BigDecimal montantTtc) {
    }

    record AbsencesDuMois(int annee, int mois, long nombre, BigDecimal montantHt) {
    }
}
