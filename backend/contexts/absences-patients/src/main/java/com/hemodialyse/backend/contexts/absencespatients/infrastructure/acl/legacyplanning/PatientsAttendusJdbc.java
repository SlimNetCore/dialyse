package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyplanning;

import com.hemodialyse.backend.contexts.absencespatients.application.PatientsAttendus;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL vers les tables {@code patients}, {@code center_holiday}, {@code center_closure_day} et {@code seances} (Planification,
 * Patient et couverture, Organisation, Séance : SQL direct tant qu'ils ne sont pas extraits, ADR 0024). Une requête par jour et par
 * centre ; l'index {@code seances (center_id, patient_id, date_seance)} (V31) sert l'anti-jointure. Règle de sortie (KB-ABS-24) :
 * décès, greffe, transfert et guérison excluent le jour de l'événement (uniformisé) ; sans date d'événement le patient n'est jamais
 * attendu (comportement legacy conservé jusqu'à la règle de Patient et couverture qui bloquera une sortie sans date). Séjour limité : attendu jusqu'à la fin de séjour incluse, ou sans fin.
 */
public class PatientsAttendusJdbc implements PatientsAttendus {

    private static final String SEANCE_REALISEE = "s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')";

    private final JdbcTemplate jdbc;

    public PatientsAttendusJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String colonneDuJour(DayOfWeek jour) {
        return switch (jour) {
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
    public List<PatientId> sansSeanceRealisee(CentreId centre, LocalDate jour) {
        Date date = Date.valueOf(jour);
        UUID centreId = centre.valeur();
        Integer fermes = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM center_holiday WHERE center_id = ? "
                        + "AND day_date = ?) + (SELECT COUNT(*) FROM center_closure_day WHERE center_id = ? AND day_date = ?)",
                Integer.class, centreId, date, centreId, date);
        if (fermes != null && fermes > 0) {
            return List.of();
        }
        return jdbc.query("SELECT p.id FROM patients p WHERE p.center_id = ? AND p." + colonneDuJour(jour.getDayOfWeek())
                        + " = TRUE AND COALESCE(p.en_sommeil, FALSE) = FALSE AND p.date_admission <= ? "
                        + "AND (p.etat_patient IS NULL "
                        + "OR p.etat_patient NOT IN ('TRANSFERE','DECEDE','GREFFE','GUERRI','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER') "
                        // séjour limité : attendu jusqu'à la fin de séjour incluse
                        + "OR (p.etat_patient IN ('OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER') "
                        + "AND (p.date_evenement_etat IS NULL OR ? <= p.date_evenement_etat)) "
                        // sorti : plus attendu le jour de l'événement ni après ; SANS date d'événement, jamais attendu (KB-ABS-24 reproduit pour ce cas)
                        + "OR (p.etat_patient IN ('TRANSFERE','DECEDE','GREFFE','GUERRI') "
                        + "AND p.date_evenement_etat IS NOT NULL AND ? < p.date_evenement_etat)) "
                        + "AND NOT EXISTS (SELECT 1 FROM seances s WHERE s.center_id = p.center_id AND s.patient_id = p.id "
                        + "AND s.date_seance = ? AND " + SEANCE_REALISEE + ") "
                        // pas de transaction ni de lecture inutiles pour un jour déjà détecté (idempotence : le contrôle reste dans la transaction)
                        + "AND NOT EXISTS (SELECT 1 FROM absence_patient a WHERE a.center_id = p.center_id AND a.patient_id = p.id "
                        + "AND a.date_seance = ?) ORDER BY p.id",
                (rs, i) -> new PatientId(rs.getObject(1, UUID.class)), centreId, date, date, date, date, date);
    }
}
