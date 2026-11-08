package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.seance.application.PatientsDuJour;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL vers la table des patients ({@code patients}, tenue par le Patient), en lecture seule : les séances d'un jour du centre avec le
 * code, le nom et le prénom du patient (jointure interne : une séance dont le patient n'existe plus n'apparaît pas), triées par nom puis
 * prénom. Reprend la requête du legacy à l'identique (tous statuts, KB-SEA-61) ; à remplacer par un langage publié du Patient.
 */
public class PatientsDuJourJdbc implements PatientsDuJour {

    private final JdbcTemplate jdbc;

    public PatientsDuJourJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PatientDuJour> deLaJournee(CentreId centre, LocalDate jour) {
        return jdbc.query("SELECT s.id AS seance_id, s.patient_id AS patient_id, s.date_seance AS date_seance, s.statut AS statut, "
                        + "p.code_patient AS code_patient, p.nom AS nom, p.prenom AS prenom FROM seances s "
                        + "INNER JOIN patients p ON p.id = s.patient_id WHERE s.center_id = ? AND s.date_seance = ? "
                        + "ORDER BY p.nom ASC, p.prenom ASC, s.id ASC",
                (rs, i) -> new PatientDuJour(new SeanceId(rs.getObject("seance_id", UUID.class)),
                        new PatientId(rs.getObject("patient_id", UUID.class)), rs.getDate("date_seance").toLocalDate(),
                        rs.getString("statut"), rs.getString("code_patient"), rs.getString("nom"), rs.getString("prenom")),
                centre.valeur(), Date.valueOf(jour));
    }
}
