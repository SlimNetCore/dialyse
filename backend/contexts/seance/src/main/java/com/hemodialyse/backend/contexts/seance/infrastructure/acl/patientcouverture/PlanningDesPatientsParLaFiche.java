package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ProgrammationDesPatients;
import com.hemodialyse.backend.contexts.seance.application.PlanningDesPatients;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Faits du planning d'un patient à une date (SE7f, ADR 0100 ; LEC-PAT sous-slice i, ADR 0176). Centre fermé = au moins un jour férié OU
 * une fermeture exceptionnelle du centre à la date ({@code center_holiday}, {@code center_closure_day}, additionnés : KB-SEA-95,
 * reproduit) ; jour de dialyse, sommeil, état et dates viennent de la programmation du patient AU CENTRE, lue par le langage publié
 * {@link ProgrammationDesPatients}. Patient inconnu ou d'un autre centre : {@link PatientIntrouvable} (404). Remplace
 * {@code PlanningDesPatientsJdbc} (SQL direct sur {@code patients}).
 */
public class PlanningDesPatientsParLaFiche implements PlanningDesPatients {

    private final JdbcTemplate jdbc;
    private final ProgrammationDesPatients programmation;

    public PlanningDesPatientsParLaFiche(JdbcTemplate jdbc, ProgrammationDesPatients programmation) {
        this.jdbc = jdbc;
        this.programmation = programmation;
    }

    @Override
    public SituationDuPlanning situation(CentreId centre, PatientId patient, LocalDate date) {
        Date jour = Date.valueOf(date);
        Integer fermes = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM center_holiday WHERE center_id = ? "
                        + "AND day_date = ?) + (SELECT COUNT(*) FROM center_closure_day WHERE center_id = ? AND day_date = ?)",
                Integer.class, centre.valeur(), jour, centre.valeur(), jour);
        return programmation.duPatient(centre.valeur(), patient.valeur())
                .map(p -> new SituationDuPlanning(fermes != null && fermes > 0, p.joursDeDialyse().contains(date.getDayOfWeek()),
                        p.enSommeil(), p.etat(), p.dateEvenement(), p.dateAdmission()))
                .orElseThrow(PatientIntrouvable::new);
    }
}
