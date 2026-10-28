package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyplanning;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.PatientsAttendus;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PresenceDesPatients;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ACL vers la Planification (qui est attendu ce jour-là : langage publié {@link PresenceDesPatients}, l'unique calcul de présence,
 * RG-PLN-045) et vers les tables {@code center_holiday}, {@code center_closure_day}, {@code seances} (Organisation, Séance : SQL
 * direct tant qu'ils ne sont pas extraits, ADR 0024). Une lecture par jour et par centre ; l'index {@code seances (center_id,
 * patient_id, date_seance)} (V31) sert la recherche des séances réalisées. Règle de sortie (KB-ABS-24) portée par la présence :
 * décès, greffe, transfert et guérison excluent le jour de l'événement ; sans date d'événement le patient n'est jamais attendu ;
 * séjour limité : attendu jusqu'à la fin de séjour incluse, ou sans fin. Un patient en sommeil n'est jamais attendu.
 */
public class PatientsAttendusJdbc implements PatientsAttendus {

    private final JdbcTemplate jdbc;
    private final PresenceDesPatients presence;

    public PatientsAttendusJdbc(JdbcTemplate jdbc, PresenceDesPatients presence) {
        this.jdbc = jdbc;
        this.presence = presence;
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
        List<UUID> attendus = presence.attendusLe(centreId, jour);
        if (attendus.isEmpty()) {
            return List.of();
        }
        // déjà réalisée ce jour-là, ou déjà détectée (pas de transaction ni de lecture inutiles : l'idempotence reste dans la transaction)
        Set<UUID> ecartes = new HashSet<>(jdbc.queryForList("SELECT s.patient_id FROM seances s WHERE s.center_id = ? AND s.date_seance = ? "
                + "AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')", UUID.class, centreId, date));
        ecartes.addAll(jdbc.queryForList("SELECT a.patient_id FROM absence_patient a WHERE a.center_id = ? AND a.date_seance = ?",
                UUID.class, centreId, date));
        return attendus.stream().filter(p -> !ecartes.contains(p)).map(PatientId::new).toList();
    }
}
