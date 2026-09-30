package com.hemodialyse.backend.domain.migration.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — soldes d'ouverture : une facture non soldée de l'ancien logiciel devient une facture « de reprise »
 * (numéro d'origine préfixé, série distincte de la numérotation continue, sans TVA ni écriture comptable),
 * accompagnée du montant déjà réglé. Le reste à payer s'encaisse ensuite normalement dans le module Règlements.
 */
public interface OpeningBalancePort {

    /**
     * Préfixe des numéros de factures de reprise : ils ne peuvent pas entrer en collision avec la série normale.
     */
    String NUMBER_PREFIX = "REPRISE-";

    /**
     * Auteur des règlements de reprise (les autres règlements ont été saisis dans la plateforme).
     */
    String MIGRATION_AUTHOR = "REPRISE";

    Optional<UUID> findByNumero(CenterId centerId, String numero);

    /**
     * Règlements saisis dans la plateforme (hors reprise) sur cette facture.
     */
    long countPaymentsOutsideMigration(CenterId centerId, UUID factureId);

    UUID create(CenterId centerId, OpeningInvoice invoice);

    /**
     * Remplace montants, dates et règlement de reprise d'une facture de reprise non encore encaissée.
     */
    void replace(CenterId centerId, UUID factureId, OpeningInvoice invoice);

    /**
     * @param patientStatus         état du patient à la date de reprise (instantané de facture)
     * @param numeroImmatriculation n° d'assurance du patient (instantané de facture)
     */
    record OpeningInvoice(UUID patientId, String numero, LocalDate dateFacture, LocalDate periodStart,
                          LocalDate periodEnd,
                          BigDecimal montantTtc, BigDecimal montantRegle, LocalDate dateReglement, String libelle,
                          String patientCode, String patientFullName, String patientStatus,
                          String numeroImmatriculation, UUID centrePayeurId) {
    }
}

