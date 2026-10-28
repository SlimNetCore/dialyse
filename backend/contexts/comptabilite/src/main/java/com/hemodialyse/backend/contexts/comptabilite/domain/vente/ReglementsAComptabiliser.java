package com.hemodialyse.backend.contexts.comptabilite.domain.vente;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant : les règlements des factures du centre, lus chez le Règlement (aucune écriture).
 */
public interface ReglementsAComptabiliser {

    /**
     * Règlements encaissés dans [du, au], par date puis identifiant.
     */
    List<ReglementAComptabiliser> encaissesEntre(CentreId centre, LocalDate du, LocalDate au);
}

