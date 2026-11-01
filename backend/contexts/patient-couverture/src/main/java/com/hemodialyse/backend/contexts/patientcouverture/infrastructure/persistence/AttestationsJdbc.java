package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroitId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AttestationsJdbc implements AttestationRepository {
    private final JdbcTemplate jdbc;

    public AttestationsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private AttestationDroit mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        LocalDate debut = rs.getDate("date_debut").toLocalDate();
        LocalDate fin = rs.getDate("date_fin").toLocalDate();
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return new AttestationDroit(new AttestationDroitId(id), new CentreId(centreId), new PatientId(patientId), debut, fin, createdAt);
    }

    @Override
    public Optional<AttestationDroit> trouver(AttestationDroitId id, CentreId centreId) {
        List<AttestationDroit> r = jdbc.query("SELECT id, center_id, patient_id, date_debut, date_fin, created_at FROM attestation_droit WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(AttestationDroit a) {
        int n = jdbc.update("UPDATE attestation_droit SET patient_id=?, date_debut=?, date_fin=? WHERE id=? AND center_id=?",
                a.patientId().valeur(),
                Date.valueOf(a.dateDebut()),
                Date.valueOf(a.dateFin()),
                a.id().valeur(), a.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO attestation_droit (id, center_id, patient_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,?)",
                    a.id().valeur(), a.centreId().valeur(), a.patientId().valeur(),
                    Date.valueOf(a.dateDebut()), Date.valueOf(a.dateFin()),
                    java.sql.Timestamp.from((a.createdAt() == null ? java.time.OffsetDateTime.now() : a.createdAt()).toInstant()));
        }
    }

    @Override
    public void supprimer(AttestationDroitId id, CentreId centreId) {
        jdbc.update("DELETE FROM attestation_droit WHERE id = ? AND center_id = ?", id.valeur(), centreId.valeur());
    }

    @Override
    public PagedResult<AttestationDroit> lister(CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ?", Long.class, centreId.valeur());
        List<AttestationDroit> items = jdbc.query("SELECT id, center_id, patient_id, date_debut, date_fin, created_at FROM attestation_droit WHERE center_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public List<AttestationDroit> listerParPatient(PatientId patientId, CentreId centreId) {
        return jdbc.query("SELECT id, center_id, patient_id, date_debut, date_fin, created_at FROM attestation_droit WHERE patient_id = ? AND center_id = ? ORDER BY created_at DESC",
                (rs, i) -> mapRow(rs), patientId.valeur(), centreId.valeur());
    }

    @Override
    public boolean existsValidAt(CentreId centreId, PatientId patientId, LocalDate date) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ? AND date_debut <= ? AND date_fin >= ?",
                Integer.class, centreId.valeur(), patientId.valeur(), Date.valueOf(date), Date.valueOf(date));
        return n != null && n > 0;
    }
}