package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.facturation.port.FacturationDashboardBucket;
import com.hemodialyse.backend.domain.facturation.port.FacturationDashboardResult;
import com.hemodialyse.backend.domain.facturation.port.FacturationDashboardStatusBucket;
import com.hemodialyse.backend.domain.facturation.port.SeanceFacturationPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Reste de l'adaptateur legacy des séances à facturer : le tableau de bord de facturation (slice F5). La lecture des séances
 * facturables et la liaison séance → facture sont servies par le contexte {@code facturation} (slices F2 et F3).
 */
@Component
public class SeanceFacturationJdbcAdapter implements SeanceFacturationPort {

    private final JdbcTemplate jdbc;

    public SeanceFacturationJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FacturationDashboardResult loadDashboard(CenterId centerId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        BigDecimal revenueTtc = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_ttc), 0) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                BigDecimal.class,
                centerId.value(),
                Date.valueOf(start),
                Date.valueOf(end)
        )).orElse(BigDecimal.ZERO);

        BigDecimal revenueHt = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_ht), 0) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                BigDecimal.class,
                centerId.value(),
                Date.valueOf(start),
                Date.valueOf(end)
        )).orElse(BigDecimal.ZERO);

        Long billedSeances = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND statut = 'FACTUREE' AND date_seance BETWEEN ? AND ?",
                Long.class,
                centerId.value(),
                Date.valueOf(start),
                Date.valueOf(end)
        )).orElse(0L);

        Long billedPatients = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT patient_id) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                Long.class,
                centerId.value(),
                Date.valueOf(start),
                Date.valueOf(end)
        )).orElse(0L);

        Long createdInvoices = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(1) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                Long.class,
                centerId.value(),
                Date.valueOf(start),
                Date.valueOf(end)
        )).orElse(0L);

        List<FacturationDashboardBucket> byInsurance = jdbc.query(
                """
                SELECT COALESCE(ca.code, 'INCONNU') AS insurance_code,
                       COALESCE(ca.nom, 'Inconnu') AS insurance_label,
                       COUNT(DISTINCT s.id) AS seances_count,
                       COUNT(DISTINCT f.patient_id) AS patients_count
                FROM factures f
                LEFT JOIN agence a ON a.id = f.agence_id_snapshot
                LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                GROUP BY COALESCE(ca.code, 'INCONNU'), COALESCE(ca.nom, 'Inconnu')
                ORDER BY patients_count DESC, insurance_code ASC
                """,
                (rs, rowNum) -> new FacturationDashboardBucket(
                        rs.getString("insurance_code"),
                        rs.getString("insurance_label"),
                        rs.getLong("seances_count"),
                        rs.getLong("patients_count")
                ),
                centerId.value(),
                Date.valueOf(start),
                Date.valueOf(end)
        );

        List<FacturationDashboardStatusBucket> byStatus = jdbc.query(
                """
                SELECT COALESCE(f.patient_status_snapshot, 'INCONNU') AS status_code,
                       COALESCE(f.patient_status_snapshot, 'Inconnu') AS status_label,
                       COUNT(DISTINCT s.id) AS seances_count
                FROM factures f
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                        GROUP BY COALESCE(f.patient_status_snapshot, 'INCONNU'),
                                 COALESCE(f.patient_status_snapshot, 'Inconnu')
                ORDER BY seances_count DESC, status_code ASC
                """,
                (rs, rowNum) -> new FacturationDashboardStatusBucket(
                        rs.getString("status_code"),
                        rs.getString("status_label"),
                        rs.getLong("seances_count")
                ),
                centerId.value(),
                Date.valueOf(start),
                Date.valueOf(end)
        );

        return new FacturationDashboardResult(
                centerId.value(),
                month,
                revenueTtc,
                revenueHt,
                billedSeances,
                billedPatients,
                createdInvoices,
                byInsurance,
                byStatus
        );
    }
}
