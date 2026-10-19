package com.hemodialyse.backend.contexts.comptabilite.domain.vente;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant : les factures du centre, lues chez la Facturation (aucune écriture), triées par date de facturation puis numéro.
 */
public interface FacturesAComptabiliser {

    /**
     * Factures dont la période facturée est incluse dans [debut, fin], sans filtre de statut (B14).
     */
    List<FactureAComptabiliser> dontLaPeriodeEstIncluseDans(CentreId centre, LocalDate debut, LocalDate fin);

    /**
     * Factures émises (date de facturation) dans [du, au].
     */
    List<FactureAComptabiliser> factureesEntre(CentreId centre, LocalDate du, LocalDate au);

    /**
     * Factures émises dans [du, au] qui n'ont aucune écriture comptable (source).
     */
    List<FactureAComptabiliser> sansEcritureEntre(CentreId centre, LocalDate du, LocalDate au);
}

