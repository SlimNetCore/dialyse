package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class SeanceBillingEligibilityJdbcAdapter implements SeanceBillingEligibilityPort {

    private final JdbcTemplate jdbc;

    public SeanceBillingEligibilityJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isPatientBillableAt(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        Long count = jdbc.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM prise_en_charge p
                        WHERE p.center_id = ?
                          AND p.patient_id = ?
                          AND p.statut = 'VALIDEE'
                          AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL
                          AND ? >= COALESCE(p.date_debut_effectif, p.date_debut_demande)
                          AND (? <= COALESCE(p.date_fin_effectif, p.date_fin_demande)
                               OR COALESCE(p.date_fin_effectif, p.date_fin_demande) IS NULL)
                          AND EXISTS (
                              SELECT 1
                              FROM attestation_droit a
                              WHERE a.center_id = p.center_id
                                AND a.patient_id = p.patient_id
                                AND ? >= a.date_debut
                                AND (? <= a.date_fin OR a.date_fin IS NULL)
                          )
                        """,
                Long.class,
                centerId.value(),
                patientId,
                Date.valueOf(dateSeance),
                Date.valueOf(dateSeance),
                Date.valueOf(dateSeance),
                Date.valueOf(dateSeance)
        );
        return count != null && count > 0;
    }
}
