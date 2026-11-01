package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePatientAssignment;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AssurePatientsJdbc implements AssurePatientRepository {
    private final JdbcTemplate jdbc;

    public AssurePatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private AssurePatientAssignment mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate debut = rs.getDate("date_debut_affectation") == null ? null : rs.getDate("date_debut_affectation").toLocalDate();
        LocalDate fin = rs.getDate("date_fin_affectation") == null ? null : rs.getDate("date_fin_affectation").toLocalDate();
        return new AssurePatientAssignment(rs.getObject("id", UUID.class),
                new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)),
                new AssureId(rs.getString("numero_assurance")),
                rs.getBoolean("is_primary"), debut, fin);
    }

    @Override
    public Optional<AssurePatientAssignment> trouverPrincipal(PatientId patientId, CentreId centreId) {
        List<AssurePatientAssignment> r = jdbc.query(
                "SELECT id, patient_id, center_id, numero_assurance, is_primary, date_debut_affectation, date_fin_affectation FROM assure_patient WHERE patient_id = ? AND center_id = ? AND date_fin_affectation IS NULL ORDER BY date_affectation DESC LIMIT 1",
                (rs, i) -> mapRow(rs), patientId.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public List<AssurePatientAssignment> listerHistorique(PatientId patientId, CentreId centreId) {
        return jdbc.query("SELECT id, patient_id, center_id, numero_assurance, is_primary, date_debut_affectation, date_fin_affectation FROM assure_patient WHERE patient_id = ? AND center_id = ? ORDER BY date_affectation DESC",
                (rs, i) -> mapRow(rs), patientId.valeur(), centreId.valeur());
    }

    @Override
    public void terminerActifs(PatientId patientId, CentreId centreId, LocalDate dateFin) {
        jdbc.update("UPDATE assure_patient SET date_fin_affectation = ? WHERE patient_id = ? AND center_id = ? AND date_fin_affectation IS NULL",
                Date.valueOf(dateFin), patientId.valeur(), centreId.valeur());
    }

    @Override
    public void sauvegarder(AssurePatientAssignment a) {
        jdbc.update("INSERT INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation, date_debut_affectation, date_fin_affectation) VALUES (?,?,?,?,?,?,?,?)",
                a.id(), a.patientId().valeur(), a.numeroAssurance().valeur(), a.centreId().valeur(), a.primary(),
                java.sql.Timestamp.from(java.time.Instant.now()), Date.valueOf(a.dateDebutAffectation() == null ? LocalDate.now() : a.dateDebutAffectation()),
                a.dateFinAffectation() == null ? null : Date.valueOf(a.dateFinAffectation()));
    }

    @Override
    public Optional<AssurePatientAssignment> trouverParId(UUID id) {
        List<AssurePatientAssignment> r = jdbc.query(
                "SELECT id, patient_id, center_id, numero_assurance, is_primary, date_debut_affectation, date_fin_affectation FROM assure_patient WHERE id = ?",
                (rs, i) -> mapRow(rs), id);
        return r.stream().findFirst();
    }
}