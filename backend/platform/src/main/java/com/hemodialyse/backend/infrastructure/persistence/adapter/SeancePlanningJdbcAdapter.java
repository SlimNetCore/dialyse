package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.SituationPlanning;
import com.hemodialyse.backend.domain.seance.port.SeancePlanningPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lit les jours de dialyse du patient, le calendrier du centre (jours fériés et fermetures) et l'état du patient
 * pour savoir s'il est attendu à une date (mêmes sources que la détection des absences).
 */
@Component
public class SeancePlanningJdbcAdapter implements SeancePlanningPort {

    private final JdbcTemplate jdbc;

    public SeancePlanningJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String colonneJour(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "jour_lundi";
            case TUESDAY -> "jour_mardi";
            case WEDNESDAY -> "jour_mercredi";
            case THURSDAY -> "jour_jeudi";
            case FRIDAY -> "jour_vendredi";
            case SATURDAY -> "jour_samedi";
            case SUNDAY -> "jour_dimanche";
        };
    }

    @Override
    public SituationPlanning situation(CenterId centerId, UUID patientId, LocalDate date) {
        Date jour = Date.valueOf(date);
        Integer fermes = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM center_holiday WHERE center_id = ? "
                        + "AND day_date = ?) + (SELECT COUNT(*) FROM center_closure_day WHERE center_id = ? AND day_date = ?)",
                Integer.class, centerId.value(), jour, centerId.value(), jour);
        return jdbc.query("SELECT p." + colonneJour(date.getDayOfWeek()) + ", COALESCE(p.en_sommeil, FALSE), "
                                + "p.etat_patient, p.date_evenement_etat, p.date_admission FROM patients p "
                                + "WHERE p.center_id = ? AND p.id = ?",
                        (rs, i) -> new SituationPlanning(fermes != null && fermes > 0, rs.getBoolean(1),
                                rs.getBoolean(2), rs.getString(3),
                                rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(),
                                rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate()),
                        centerId.value(), patientId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("PATIENT_INTROUVABLE", "Patient introuvable"));
    }
}
