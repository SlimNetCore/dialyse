package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.AbsenceInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Absences des infirmiers sur la table {@code infirmier_absence}.
 */
@Component
public class AbsenceInfirmierJdbcAdapter implements AbsenceInfirmierRepositoryPort {

    private static final String COLONNES = "id, center_id, infirmier_id, date_debut, date_fin, type, motif";

    private static final RowMapper<AbsenceInfirmier> MAPPER = (rs, i) -> new AbsenceInfirmier(
            rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
            rs.getObject("infirmier_id", UUID.class), rs.getDate("date_debut").toLocalDate(),
            rs.getDate("date_fin").toLocalDate(), TypeAbsence.valueOf(rs.getString("type")), rs.getString("motif"));

    private final JdbcTemplate jdbc;

    public AbsenceInfirmierJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AbsenceInfirmier save(AbsenceInfirmier a) {
        int maj = jdbc.update("UPDATE infirmier_absence SET date_debut = ?, date_fin = ?, type = ?, motif = ? "
                        + "WHERE id = ? AND center_id = ?",
                Date.valueOf(a.debut()), Date.valueOf(a.fin()), a.type().name(), a.motif(), a.id(), a.centerId());
        if (maj == 0) {
            jdbc.update("INSERT INTO infirmier_absence (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                    a.id(), a.centerId(), a.infirmierId(), Date.valueOf(a.debut()), Date.valueOf(a.fin()),
                    a.type().name(), a.motif());
        }
        return a;
    }

    @Override
    public Optional<AbsenceInfirmier> findById(UUID centerId, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier_absence WHERE center_id = ? AND id = ?",
                MAPPER, centerId, id).stream().findFirst();
    }

    @Override
    public PagedResult<AbsenceInfirmier> findPaged(UUID centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_absence WHERE center_id = ?", Long.class,
                centerId);
        List<AbsenceInfirmier> items = jdbc.query("SELECT " + COLONNES + " FROM infirmier_absence WHERE center_id = ? "
                        + "ORDER BY date_debut DESC, id LIMIT ? OFFSET ?",
                MAPPER, centerId, size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public PagedResult<AbsenceInfirmier> findPagedByInfirmier(UUID centerId, UUID infirmierId, int page, int size) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM infirmier_absence WHERE center_id = ? AND infirmier_id = ?", Long.class,
                centerId, infirmierId);
        List<AbsenceInfirmier> items = jdbc.query("SELECT " + COLONNES + " FROM infirmier_absence "
                        + "WHERE center_id = ? AND infirmier_id = ? ORDER BY date_debut DESC, id LIMIT ? OFFSET ?",
                MAPPER, centerId, infirmierId, size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public void delete(UUID centerId, UUID id) {
        jdbc.update("DELETE FROM infirmier_absence WHERE center_id = ? AND id = ?", centerId, id);
    }
}
