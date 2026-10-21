package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.YearMonth;

/**
 * Port sortant : la période comptable du même mois est-elle clôturée ? (RG-FAC-044). Adapter : ACL vers le langage publié de la
 * Comptabilité.
 */
public interface ClotureComptable {

    boolean estCloturee(CentreId centre, YearMonth mois);
}
