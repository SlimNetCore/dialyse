package com.hemodialyse.backend.infrastructure.persistence.adapter.absence;

import com.hemodialyse.backend.domain.absence.port.ForfaitAbsencePort;
import com.hemodialyse.backend.domain.comptabilite.port.ParametrageFiscalPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Forfait (prix HT) de la prise en charge du patient à une date — effectif d'abord, sinon demandé ; à défaut de prise
 * en charge couvrant la date, la plus récente portant un forfait — et taux de TVA du type « HEMODIALYSE » actif.
 */
@Component
public class ForfaitAbsenceJdbcAdapter implements ForfaitAbsencePort {

    static final String TYPE_PRESTATION = "HEMODIALYSE";

    private static final String SELECT_FORFAIT = "SELECT f.id, f.libelle, f.prix FROM prise_en_charge p "
            + "JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id) AND f.center_id = p.center_id "
            + "WHERE p.center_id = ? AND p.patient_id = ? ";
    private static final String ORDRE = "ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END, "
            + "p.created_at DESC LIMIT 1";

    private final JdbcTemplate jdbc;
    private final ParametrageFiscalPort fiscal;

    public ForfaitAbsenceJdbcAdapter(JdbcTemplate jdbc, ParametrageFiscalPort fiscal) {
        this.jdbc = jdbc;
        this.fiscal = fiscal;
    }

    @Override
    public Optional<ForfaitPec> forfaitPriseEnCharge(UUID centerId, UUID patientId, LocalDate date) {
        Date jour = Date.valueOf(date);
        Optional<ForfaitPec> actif = jdbc.query(SELECT_FORFAIT
                        + "AND ((p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif "
                        + "AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL)) "
                        + "OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande "
                        + "AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL))) " + ORDRE,
                (rs, i) -> new ForfaitPec(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)),
                centerId, patientId, jour, jour, jour, jour).stream().findFirst();
        if (actif.isPresent()) return actif;
        return jdbc.query(SELECT_FORFAIT + "AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL "
                        + ORDRE,
                (rs, i) -> new ForfaitPec(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)),
                centerId, patientId).stream().findFirst();
    }

    @Override
    public BigDecimal tauxTva(UUID centerId, LocalDate date) {
        return fiscal.findActiveAt(centerId, TYPE_PRESTATION, date)
                .filter(r -> !r.exonere())
                .map(RegleTVA::tauxApplique)
                .orElse(BigDecimal.ZERO);
    }
}
