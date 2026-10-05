package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Component
public class SeanceBillingEligibilityJdbcAdapter implements SeanceBillingEligibilityPort {

    /**
     * Prise en charge avec forfait dont une période couvre la date : la période <b>effective</b> (début et fin
     * ensemble) ou, à défaut, la période <b>demandée</b> — même règle que la résolution du forfait de la séance.
     * Ne pas mélanger les bornes (début effectif avec fin demandée) : une période effective ouverte reste valable.
     */
    private static final String PEC_COUVRANTE = """
            FROM prise_en_charge p
            WHERE p.center_id = ?
              AND p.patient_id = ?
              AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL
              AND (
                    (p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif
                        AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL))
                 OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande
                        AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL))
              )
            """;

    private static final String VALIDEE = " AND p.statut = 'VALIDEE' ";

    private static final String ATTESTATION_COUVRANTE = """
              AND EXISTS (
                  SELECT 1
                  FROM attestation_droit a
                  WHERE a.center_id = p.center_id
                    AND a.patient_id = p.patient_id
                    AND ? >= a.date_debut
                    AND (? <= a.date_fin OR a.date_fin IS NULL)
              )
            """;

    private final JdbcTemplate jdbc;

    public SeanceBillingEligibilityJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isPatientBillableAt(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        return causeNonFacturable(centerId, patientId, dateSeance).isEmpty();
    }

    @Override
    public Optional<CauseNonFacturable> causeNonFacturable(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        Date jour = Date.valueOf(dateSeance);
        if (compter("SELECT COUNT(1) " + PEC_COUVRANTE, centerId, patientId, jour, jour, jour, jour) == 0) {
            return Optional.of(CauseNonFacturable.PRISE_EN_CHARGE);
        }
        if (compter("SELECT COUNT(1) " + PEC_COUVRANTE + VALIDEE, centerId, patientId, jour, jour, jour, jour) == 0) {
            return Optional.of(CauseNonFacturable.PRISE_EN_CHARGE_NON_VALIDEE);
        }
        if (compter("SELECT COUNT(1) " + PEC_COUVRANTE + VALIDEE + ATTESTATION_COUVRANTE, centerId, patientId, jour,
                jour, jour, jour, jour, jour) == 0) {
            return Optional.of(CauseNonFacturable.ATTESTATION_DROITS);
        }
        return Optional.empty();
    }

    private long compter(String sql, CenterId centerId, UUID patientId, Object... dates) {
        Object[] args = new Object[2 + dates.length];
        args[0] = centerId.value();
        args[1] = patientId;
        System.arraycopy(dates, 0, args, 2, dates.length);
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }
}
