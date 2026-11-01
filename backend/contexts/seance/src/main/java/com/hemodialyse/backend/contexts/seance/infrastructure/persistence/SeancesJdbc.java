package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRepository;
import com.hemodialyse.backend.contexts.seance.domain.StatutSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SeancesJdbc implements SeanceRepository {
    private final JdbcTemplate jdbc;

    public SeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Seance mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        UUID pecId = rs.getObject("pec_id", UUID.class);
        LocalDate dateSeance = rs.getDate("date_seance") == null ? null : rs.getDate("date_seance").toLocalDate();
        String type = rs.getString("type_seance");
        StatutSeance statut = StatutSeance.valueOf(rs.getString("statut"));
        boolean verrouille = false;
        try {
            verrouille = rs.getBoolean("verrouille_facturation");
        } catch (java.sql.SQLException ignored) {
        }
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return new Seance(new SeanceId(id), new CentreId(centreId), new com.hemodialyse.backend.contexts.seance.domain.PatientId(patientId),
                new com.hemodialyse.backend.contexts.seance.domain.PecId(pecId), dateSeance, type, statut, verrouille, createdAt);
    }

    @Override
    public Optional<Seance> trouver(SeanceId id, CentreId centreId) {
        List<Seance> r = jdbc.query("SELECT id, center_id, patient_id, pec_id, date_seance, type_seance, statut, created_at FROM seances WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Seance s) {
        int n = jdbc.update("UPDATE seances SET patient_id=?, pec_id=?, date_seance=?, type_seance=?, statut=?, verrouille_facturation=? WHERE id=? AND center_id=?",
                s.patientId().valeur(), s.pecId().valeur(),
                s.dateSeance() == null ? null : Date.valueOf(s.dateSeance()),
                s.typeSeance(), s.statut().name(), s.verrouilleFacturation(), s.id().valeur(), s.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO seances (id, center_id, patient_id, pec_id, date_seance, type_seance, statut, verrouille_facturation, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    s.id().valeur(), s.centreId().valeur(), s.patientId().valeur(), s.pecId().valeur(),
                    s.dateSeance() == null ? null : Date.valueOf(s.dateSeance()),
                    s.typeSeance(), s.statut().name(), s.verrouilleFacturation(),
                    java.sql.Timestamp.from(s.createdAt().toInstant()));
        }
    }

    @Override
    public void supprimer(SeanceId id, CentreId centreId) {
        jdbc.update("DELETE FROM seances WHERE id = ? AND center_id = ?", id.valeur(), centreId.valeur());
    }

    @Override
    public PagedResult<Seance> lister(CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ?", Long.class, centreId.valeur());
        List<Seance> items = jdbc.query("SELECT id, center_id, patient_id, pec_id, date_seance, type_seance, statut, created_at FROM seances WHERE center_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public PagedResult<Seance> listerDuJour(CentreId centreId, LocalDate date, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND date_seance = ?", Long.class, centreId.valeur(), Date.valueOf(date));
        List<Seance> items = jdbc.query("SELECT id, center_id, patient_id, pec_id, date_seance, type_seance, statut, created_at FROM seances WHERE center_id = ? AND date_seance = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), Date.valueOf(date), size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public List<Seance> listerParPatient(com.hemodialyse.backend.contexts.seance.domain.PatientId patientId, CentreId centreId) {
        return jdbc.query("SELECT id, center_id, patient_id, pec_id, date_seance, type_seance, statut, created_at, verrouille_facturation FROM seances WHERE patient_id = ? AND center_id = ? ORDER BY created_at DESC",
                (rs, i) -> mapRow(rs), patientId.valeur(), centreId.valeur());
    }

    @Override
    public void verrouillerPourFacturation(CentreId centreId, java.time.LocalDate debut, java.time.LocalDate fin) {
        jdbc.update("UPDATE seances SET verrouille_facturation = TRUE WHERE center_id = ? AND date_seance >= ? AND date_seance <= ?",
                centreId.valeur(), Date.valueOf(debut), Date.valueOf(fin));
    }
}