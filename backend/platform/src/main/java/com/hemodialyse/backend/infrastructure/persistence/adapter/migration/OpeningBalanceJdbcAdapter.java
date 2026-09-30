package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.domain.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Soldes d'ouverture (JDBC) : facture de reprise hors séquence de numérotation, sans TVA (TTC = HT), une ligne
 * « Solde d'ouverture » et, le cas échéant, le montant déjà réglé saisi comme règlement de reprise.
 * Aucune écriture comptable n'est générée (les soldes figurent déjà dans la comptabilité de l'ancien système).
 */
@Component
public class OpeningBalanceJdbcAdapter implements OpeningBalancePort {

    private final JdbcTemplate jdbc;

    public OpeningBalanceJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findByNumero(CenterId centerId, String numero) {
        return jdbc.queryForList("SELECT id FROM factures WHERE center_id = ? AND numero_facture = ?", UUID.class,
                centerId.value(), numero).stream().findFirst();
    }

    @Override
    public long countPaymentsOutsideMigration(CenterId centerId, UUID factureId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM facture_reglements WHERE facture_id = ? AND center_id = ? "
                + "AND (saisi_par IS NULL OR saisi_par <> ?)", Long.class, factureId, centerId.value(), MIGRATION_AUTHOR);
        return count == null ? 0 : count;
    }

    @Override
    public UUID create(CenterId centerId, OpeningInvoice invoice) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name, "
                        + "patient_status_snapshot, numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, "
                        + "period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?, ?)",
                id, centerId.value(), invoice.patientId(), invoice.numero(), invoice.patientCode(), invoice.patientFullName(),
                invoice.patientStatus(), invoice.numeroImmatriculation(), invoice.centrePayeurId(),
                agenceOf(centerId, invoice.centrePayeurId()), invoice.periodStart(), invoice.periodEnd(), invoice.dateFacture(),
                invoice.montantTtc(), invoice.montantTtc(), OffsetDateTime.now());
        insertDetails(centerId, id, invoice);
        return id;
    }

    @Override
    public void replace(CenterId centerId, UUID factureId, OpeningInvoice invoice) {
        jdbc.update("UPDATE factures SET period_start = ?, period_end = ?, date_facturation = ?, total_ht = ?, total_ttc = ? "
                        + "WHERE id = ? AND center_id = ?",
                invoice.periodStart(), invoice.periodEnd(), invoice.dateFacture(), invoice.montantTtc(), invoice.montantTtc(),
                factureId, centerId.value());
        jdbc.update("DELETE FROM facture_lignes WHERE facture_id = ? AND center_id = ?", factureId, centerId.value());
        jdbc.update("DELETE FROM facture_reglements WHERE facture_id = ? AND center_id = ? AND saisi_par = ?",
                factureId, centerId.value(), MIGRATION_AUTHOR);
        insertDetails(centerId, factureId, invoice);
    }

    private void insertDetails(CenterId centerId, UUID factureId, OpeningInvoice invoice) {
        jdbc.update("INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, "
                        + "seance_count, line_ht) VALUES (?, ?, ?, NULL, ?, ?, 1, ?)",
                UUID.randomUUID(), factureId, centerId.value(), invoice.libelle(), invoice.montantTtc(), invoice.montantTtc());
        if (invoice.montantRegle() != null && invoice.montantRegle().compareTo(BigDecimal.ZERO) > 0) {
            UUID reglementId = UUID.randomUUID();
            jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, "
                            + "code_reglement, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    reglementId, factureId, centerId.value(), invoice.montantRegle(), invoice.dateReglement(),
                    MIGRATION_AUTHOR, "REP-" + reglementId.toString().substring(0, 8).toUpperCase(), OffsetDateTime.now());
        }
    }

    private UUID agenceOf(CenterId centerId, UUID centrePayeurId) {
        if (centrePayeurId == null) return null;
        return jdbc.queryForList("SELECT agence_id FROM centre_payeur WHERE id = ? AND center_id = ?", UUID.class,
                centrePayeurId, centerId.value()).stream().findFirst().orElse(null);
    }
}

