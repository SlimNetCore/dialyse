package com.hemodialyse.backend.contexts.comptabilite.domain.vente;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant : les règlements des factures du centre, lus chez le Règlement (aucune écriture).
 */
public interface ReglementsAComptabiliser {

    /**
     * Identifiant du règlement enregistré (facture, date, montant, auteur ; le plus récent), sinon un identifiant dérivé
     * de ces valeurs, stable d'un appel à l'autre (B13).
     */
    UUID identifiantDuReglement(CentreId centre, UUID facture, BigDecimal montant, LocalDate date, String auteur);

    /**
     * Règlements encaissés dans [du, au], par date puis identifiant.
     */
    List<ReglementAComptabiliser> encaissesEntre(CentreId centre, LocalDate du, LocalDate au);
}

