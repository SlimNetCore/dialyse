package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.facturation.valueobject.FacturationPeriod;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Component
public class SeanceFacturationJdbcAdapter implements SeanceFacturationPort {

    private final JdbcTemplate jdbc;

    public SeanceFacturationJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SeanceFacturationCandidate> findEligibleSeances(CenterId centerId, FacturationPeriod period) {
        List<Map<String, Object>> seances = jdbc.queryForList(
                """
                SELECT s.id AS seance_id,
                       s.center_id,
                       s.patient_id,
                       s.date_seance,
                       s.statut,
                       s.facture_id,
                       p.code_patient,
                       p.nom,
                       p.prenom,
                       p.etat_patient,
                       p.numero_assurance,
                       p.centre_payeur_id,
                       cp.agence_id
                FROM seances s
                INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id
                LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id AND cp.center_id = p.center_id
                WHERE s.center_id = ?
                  AND s.date_seance BETWEEN ? AND ?
                  AND s.statut IN ('VALIDEE','SIGNEE')
                ORDER BY p.nom, p.prenom, s.date_seance, s.id
                """,
                centerId.value(),
                Date.valueOf(period.startDate()),
                Date.valueOf(period.endDate())
        );

        List<SeanceFacturationCandidate> candidates = new ArrayList<>();
        for (Map<String, Object> row : seances) {
            UUID patientId = (UUID) row.get("patient_id");
            LocalDate date = ((Date) row.get("date_seance")).toLocalDate();
            ForfaitSnapshot forfait = loadForfait(centerId.value(), patientId, date);
            candidates.add(new SeanceFacturationCandidate(
                    (UUID) row.get("seance_id"),
                    (UUID) row.get("center_id"),
                    patientId,
                    date,
                    Objects.toString(row.get("statut"), null),
                    (UUID) row.get("facture_id"),
                    Objects.toString(row.get("code_patient"), null),
                    Objects.toString(row.get("nom"), null),
                    Objects.toString(row.get("prenom"), null),
                    Objects.toString(row.get("etat_patient"), null),
                    Objects.toString(row.get("numero_assurance"), null),
                    (UUID) row.get("centre_payeur_id"),
                    (UUID) row.get("agence_id"),
                    Objects.toString(row.get("numero_assurance"), null),
                    forfait.forfaitId,
                    forfait.forfaitLabel,
                    forfait.forfaitPrix
            ));
        }
        return candidates;
    }

    @Override
    public void markAsBilled(CenterId centerId, Map<UUID, UUID> seanceToFactureId) {
        for (Map.Entry<UUID, UUID> entry : seanceToFactureId.entrySet()) {
            int updated = jdbc.update(
                    """
                    UPDATE seances
                    SET statut = 'FACTUREE', facture_id = ?
                    WHERE id = ?
                      AND center_id = ?
                      AND facture_id IS NULL
                      AND statut IN ('VALIDEE','SIGNEE')
                    """,
                    entry.getValue(),
                    entry.getKey(),
                    centerId.value()
            );
            if (updated != 1) {
                throw new IllegalStateException("Une ou plusieurs seances ne sont plus eligibles a la validation");
            }
        }
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
                SELECT COALESCE(f.numero_immatriculation_snapshot, 'INCONNU') AS insurance_code,
                       COALESCE(f.numero_immatriculation_snapshot, 'Inconnu') AS insurance_label,
                       COUNT(DISTINCT s.id) AS seances_count,
                       COUNT(DISTINCT f.patient_id) AS patients_count
                FROM factures f
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                GROUP BY COALESCE(f.numero_immatriculation_snapshot, 'INCONNU')
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
                GROUP BY COALESCE(f.patient_status_snapshot, 'INCONNU')
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
                billedSeances,
                billedPatients,
                createdInvoices,
                byInsurance,
                byStatus
        );
    }

    private ForfaitSnapshot loadForfait(UUID centerId, UUID patientId, LocalDate referenceDate) {
        List<ForfaitSnapshot> activeRows = jdbc.query(
                """
                SELECT f.id AS forfait_id,
                       COALESCE(f.libelle, f.nom) AS forfait_label,
                       f.prix AS forfait_prix
                FROM prise_en_charge p
                INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id)
                                     AND f.center_id = p.center_id
                WHERE p.center_id = ?
                  AND p.patient_id = ?
                  AND (
                        (p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL))
                     OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL))
                  )
                ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END,
                         p.created_at DESC
                FETCH FIRST 1 ROWS ONLY
                """,
                (rs, rowNum) -> new ForfaitSnapshot(
                        rs.getObject("forfait_id", UUID.class),
                        rs.getString("forfait_label"),
                        rs.getBigDecimal("forfait_prix") == null ? BigDecimal.ZERO : rs.getBigDecimal("forfait_prix")
                ),
                centerId,
                patientId,
                Date.valueOf(referenceDate),
                Date.valueOf(referenceDate),
                Date.valueOf(referenceDate),
                Date.valueOf(referenceDate)
        );

        if (!activeRows.isEmpty()) {
            return activeRows.getFirst();
        }

        List<ForfaitSnapshot> fallbackRows = jdbc.query(
                """
                SELECT f.id AS forfait_id,
                       COALESCE(f.libelle, f.nom) AS forfait_label,
                       f.prix AS forfait_prix
                FROM prise_en_charge p
                INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id)
                                     AND f.center_id = p.center_id
                WHERE p.center_id = ?
                  AND p.patient_id = ?
                  AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL
                ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END,
                         p.created_at DESC
                FETCH FIRST 1 ROWS ONLY
                """,
                (rs, rowNum) -> new ForfaitSnapshot(
                        rs.getObject("forfait_id", UUID.class),
                        rs.getString("forfait_label"),
                        rs.getBigDecimal("forfait_prix") == null ? BigDecimal.ZERO : rs.getBigDecimal("forfait_prix")
                ),
                centerId,
                patientId
        );

        return fallbackRows.isEmpty() ? new ForfaitSnapshot(null, "Forfait non defini", BigDecimal.ZERO) : fallbackRows.getFirst();
    }

    private static final class ForfaitSnapshot {
        private final UUID forfaitId;
        private final String forfaitLabel;
        private final BigDecimal forfaitPrix;

        private ForfaitSnapshot(UUID forfaitId, String forfaitLabel, BigDecimal forfaitPrix) {
            this.forfaitId = forfaitId;
            this.forfaitLabel = forfaitLabel;
            this.forfaitPrix = forfaitPrix;
        }
    }
}


