package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ProgrammationDesPatients;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter du port publié {@link ProgrammationDesPatients} sur la table {@code patients} : les deux requêtes que la Séance faisait en
 * SQL direct (tableau de bord mensuel, planning d'un scan), reprises à l'identique. Lecture tolérante comme le legacy : un booléen peut
 * être un nombre (non nul : vrai), une date peut être {@link LocalDate} ou {@link Date} (autre type : absente).
 */
public class ProgrammationPublieeJdbc implements ProgrammationDesPatients {

    private static final String COLONNES = "id, sexe, date_naissance, date_admission, en_sommeil, etat_patient, date_evenement_etat, "
            + "jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, jour_dimanche";

    private static final RowMapper<ProgrammationDuPatient> LIGNE = (rs, i) -> {
        EnumSet<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
        ajouterSi(jours, DayOfWeek.MONDAY, rs.getObject("jour_lundi"));
        ajouterSi(jours, DayOfWeek.TUESDAY, rs.getObject("jour_mardi"));
        ajouterSi(jours, DayOfWeek.WEDNESDAY, rs.getObject("jour_mercredi"));
        ajouterSi(jours, DayOfWeek.THURSDAY, rs.getObject("jour_jeudi"));
        ajouterSi(jours, DayOfWeek.FRIDAY, rs.getObject("jour_vendredi"));
        ajouterSi(jours, DayOfWeek.SATURDAY, rs.getObject("jour_samedi"));
        ajouterSi(jours, DayOfWeek.SUNDAY, rs.getObject("jour_dimanche"));
        return new ProgrammationDuPatient(rs.getObject("id", UUID.class), rs.getString("sexe"), date(rs.getObject("date_naissance")),
                date(rs.getObject("date_admission")), booleen(rs.getObject("en_sommeil")), rs.getString("etat_patient"),
                date(rs.getObject("date_evenement_etat")), jours);
    };

    private final JdbcTemplate jdbc;

    public ProgrammationPublieeJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProgrammationDuPatient> duCentre(UUID centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM patients WHERE center_id = ?", LIGNE, centre);
    }

    @Override
    public Optional<ProgrammationDuPatient> duPatient(UUID centre, UUID patient) {
        return jdbc.query("SELECT " + COLONNES + " FROM patients WHERE center_id = ? AND id = ?", LIGNE, centre, patient).stream()
                .findFirst();
    }

    private static void ajouterSi(EnumSet<DayOfWeek> jours, DayOfWeek jour, Object valeur) {
        if (booleen(valeur)) jours.add(jour);
    }

    private static boolean booleen(Object valeur) {
        if (valeur instanceof Boolean b) return b;
        if (valeur instanceof Number n) return n.intValue() != 0;
        return false;
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate d) return d;
        if (valeur instanceof Date d) return d.toLocalDate();
        return null;
    }
}
