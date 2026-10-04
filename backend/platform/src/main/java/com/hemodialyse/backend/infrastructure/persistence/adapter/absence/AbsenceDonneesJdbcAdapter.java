package com.hemodialyse.backend.infrastructure.persistence.adapter.absence;

import com.hemodialyse.backend.domain.absence.port.AbsenceDonneesPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Programme des patients, calendrier du centre et séances réalisées, lus pour détecter les absences.
 */
@Component
public class AbsenceDonneesJdbcAdapter implements AbsenceDonneesPort {

    private static final String SEANCE_REALISEE = "s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')";

    private final JdbcTemplate jdbc;

    public AbsenceDonneesJdbcAdapter(JdbcTemplate jdbc) {
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
    public List<UUID> patientsAttendusSansSeance(UUID centerId, LocalDate date) {
        Date jour = Date.valueOf(date);
        Integer fermes = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM center_holiday WHERE center_id = ? "
                        + "AND day_date = ?) + (SELECT COUNT(*) FROM center_closure_day WHERE center_id = ? AND day_date = ?)",
                Integer.class, centerId, jour, centerId, jour);
        if (fermes != null && fermes > 0) return List.of();
        return jdbc.query("SELECT p.id FROM patients p WHERE p.center_id = ? AND p." + colonneJour(date.getDayOfWeek())
                        + " = TRUE AND COALESCE(p.en_sommeil, FALSE) = FALSE AND p.date_admission <= ? "
                        // patient sorti (transfert, décès, greffe, guérison) : plus attendu à partir de la libération
                        // de sa place (cf. FinOccupation) ; sans date d'évènement, il n'est plus attendu du tout
                        + "AND (p.etat_patient IS NULL "
                        + "OR p.etat_patient NOT IN ('TRANSFERE','DECEDE','GREFFE','GUERRI','OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER') "
                        // séjour limité (occasionnel, vacancier) : attendu jusqu'à la fin de séjour incluse
                        + "OR (p.etat_patient IN ('OCCASIONNEL','VACANCIER_LOCAL','VACANCIER_ETRANGER') "
                        + "AND (p.date_evenement_etat IS NULL OR ? <= p.date_evenement_etat)) "
                        + "OR (p.date_evenement_etat IS NOT NULL AND ((p.etat_patient IN ('DECEDE','GREFFE') "
                        + "AND ? < p.date_evenement_etat) OR (p.etat_patient IN ('TRANSFERE','GUERRI') "
                        + "AND ? <= p.date_evenement_etat)))) "
                        + "AND NOT EXISTS (SELECT 1 FROM seances s WHERE s.center_id = p.center_id AND s.patient_id = p.id "
                        + "AND s.date_seance = ? AND " + SEANCE_REALISEE + ") ORDER BY p.id",
                (rs, i) -> rs.getObject(1, UUID.class), centerId, jour, jour, jour, jour, jour);
    }

    @Override
    public boolean seanceRealisee(UUID centerId, UUID patientId, LocalDate date) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seances s WHERE s.center_id = ? AND s.patient_id = ? "
                        + "AND s.date_seance = ? AND " + SEANCE_REALISEE, Integer.class, centerId, patientId,
                Date.valueOf(date));
        return n != null && n > 0;
    }

    @Override
    public List<SeanceRealisee> seancesRealisees(UUID centerId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT DISTINCT s.patient_id, s.date_seance FROM seances s WHERE s.center_id = ? "
                        + "AND s.date_seance BETWEEN ? AND ? AND " + SEANCE_REALISEE + " ORDER BY s.date_seance",
                (rs, i) -> new SeanceRealisee(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate()),
                centerId, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public boolean periodeFacturee(UUID centerId, UUID patientId, LocalDate date) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM factures WHERE center_id = ? AND patient_id = ? "
                        + "AND period_start <= ? AND period_end >= ?", Integer.class, centerId, patientId, Date.valueOf(date),
                Date.valueOf(date));
        return n != null && n > 0;
    }

    @Override
    public Optional<String> nomPatient(UUID centerId, UUID patientId) {
        return jdbc.query("SELECT prenom, nom FROM patients WHERE center_id = ? AND id = ?",
                        (rs, i) -> AbsencePatientJdbcAdapter.nomComplet(rs.getString(1), rs.getString(2)), centerId, patientId)
                .stream().findFirst();
    }

    @Override
    public List<UUID> centresActifs() {
        return jdbc.query("SELECT id FROM centers", (rs, i) -> rs.getObject(1, UUID.class));
    }
}
