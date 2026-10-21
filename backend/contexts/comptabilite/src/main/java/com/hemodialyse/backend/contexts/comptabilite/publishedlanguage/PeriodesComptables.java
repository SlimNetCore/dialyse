package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Langage publié des périodes comptables (RG-CPT-005), en lecture : la Facturation y lit l'état de la période comptable d'un mois
 * pour refuser de rouvrir un mois de facturation dont la comptabilité est close (RG-FAC-044, slice F7). La Comptabilité reste
 * propriétaire de ses périodes ; un mois sans ligne est ouvert.
 */
public interface PeriodesComptables {

    /**
     * La période comptable de ce mois, pour ce centre, est-elle clôturée ?
     */
    boolean estCloturee(UUID centre, YearMonth mois);
}
