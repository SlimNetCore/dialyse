package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.facturation.aggregate.FactureAggregate;
import com.hemodialyse.backend.domain.facturation.entity.LigneFacture;
import com.hemodialyse.backend.domain.facturation.port.FactureRepositoryPort;
import com.hemodialyse.backend.domain.facturation.valueobject.FactureNumberTemplate;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class FactureJdbcAdapter implements FactureRepositoryPort {

    private final JdbcTemplate jdbc;

    public FactureJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveAll(List<FactureAggregate> factures) {
        for (FactureAggregate facture : factures) {
            jdbc.update(
                    """
                            INSERT INTO factures (
                              id, center_id, patient_id, numero_facture, patient_code, patient_full_name,
                              patient_status_snapshot, numero_immatriculation_snapshot, centre_payeur_id_snapshot,
                              agence_id_snapshot, period_start, period_end, date_facturation, tva_rate,
                              total_ht, total_tva, total_ttc, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                            """,
                    facture.getId(),
                    facture.getCenterId(),
                    facture.getPatientId(),
                    facture.getNumeroFacture(),
                    facture.getPatientCode(),
                    facture.getPatientFullName(),
                    facture.getPatientStatusSnapshot(),
                    facture.getNumeroImmatriculationSnapshot(),
                    facture.getCentrePayeurIdSnapshot(),
                    facture.getAgenceIdSnapshot(),
                    Date.valueOf(facture.getPeriod().startDate()),
                    Date.valueOf(facture.getPeriod().endDate()),
                    Date.valueOf(facture.getDateFacturation()),
                    facture.getTvaRate(),
                    facture.getTotalHt(),
                    facture.getTotalTva(),
                    facture.getTotalTtc()
            );

            for (LigneFacture ligne : facture.getLignes()) {
                jdbc.update(
                        """
                                INSERT INTO facture_lignes (
                                  id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        UUID.randomUUID(),
                        facture.getId(),
                        facture.getCenterId(),
                        ligne.getForfaitId(),
                        ligne.getForfaitLabel(),
                        ligne.getUnitPriceHt(),
                        ligne.getSeanceCount(),
                        ligne.getLineHt()
                );
            }
        }
    }

    @Override
    public int currentInvoiceSequence(CenterId centerId, LocalDate billingDate) {
        int year = billingDate.getYear();
        var rows = jdbc.queryForList(
                "SELECT seq_value FROM facture_sequence WHERE center_id = ? AND seq_year = ?",
                Integer.class,
                centerId.value(),
                year
        );
        return rows.isEmpty() ? 0 : rows.getFirst();
    }

    @Override
    public String nextInvoiceNumber(CenterId centerId, String codeFormat, LocalDate billingDate) {
        int year = billingDate.getYear();
        var rows = jdbc.queryForList(
                "SELECT seq_value FROM facture_sequence WHERE center_id = ? AND seq_year = ?",
                Integer.class,
                centerId.value(),
                year
        );
        int next = rows.isEmpty() ? 1 : rows.getFirst() + 1;
        if (rows.isEmpty()) {
            jdbc.update(
                    "INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, ?)",
                    centerId.value(),
                    year,
                    next
            );
        } else {
            jdbc.update(
                    "UPDATE facture_sequence SET seq_value = ? WHERE center_id = ? AND seq_year = ?",
                    next,
                    centerId.value(),
                    year
            );
        }
        return new FactureNumberTemplate(codeFormat).format(centerId, billingDate, next);
    }
}

