package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.seance.application.PlanningDesPatients;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Faits du planning d'un patient à une date (SE7f, ADR 0100) : requêtes reprises MOT POUR MOT de {@code SeancePlanningJdbcAdapter}
 * (plateforme, retiré). Centre fermé = au moins un jour férié OU une fermeture exceptionnelle du centre à la date ({@code center_holiday},
 * {@code center_closure_day}, additionnés : KB-SEA-95, reproduit) ; jour de dialyse, sommeil, état et dates lus sur la fiche du patient
 * AU CENTRE ({@code patients}, lecture transitoire inscrite dans {@code TableDuPatientCouvertureTest}). Patient inconnu ou d'un autre
 * centre : {@link PatientIntrouvable} (404), comme {@code PlanningFourniLegacy} traduisait {@code PATIENT_INTROUVABLE}.
 */
public class PlanningDesPatientsJdbc implements PlanningDesPatients {

    private final JdbcTemplate jdbc;

    public PlanningDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String colonneJour(DayOfWeek d) {
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
    public SituationDuPlanning situation(CentreId centre, PatientId patient, LocalDate date) {
        Date jour = Date.valueOf(date);
        Integer fermes = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM center_holiday WHERE center_id = ? "
                        + "AND day_date = ?) + (SELECT COUNT(*) FROM center_closure_day WHERE center_id = ? AND day_date = ?)",
                Integer.class, centre.valeur(), jour, centre.valeur(), jour);
        return jdbc.query("SELECT p." + colonneJour(date.getDayOfWeek()) + ", COALESCE(p.en_sommeil, FALSE), "
                                + "p.etat_patient, p.date_evenement_etat, p.date_admission FROM patients p "
                                + "WHERE p.center_id = ? AND p.id = ?",
                        (rs, i) -> new SituationDuPlanning(fermes != null && fermes > 0, rs.getBoolean(1),
                                rs.getBoolean(2), rs.getString(3),
                                rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(),
                                rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate()),
                        centre.valeur(), patient.valeur())
                .stream().findFirst()
                .orElseThrow(PatientIntrouvable::new);
    }
}
