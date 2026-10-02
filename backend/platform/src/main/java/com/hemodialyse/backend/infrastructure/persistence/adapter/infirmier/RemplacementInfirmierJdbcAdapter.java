package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.RemplacementInfirmierRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Remplacements ponctuels sur la table {@code infirmier_remplacement}.
 */
@Component
public class RemplacementInfirmierJdbcAdapter implements RemplacementInfirmierRepositoryPort {

    private static final String COLONNES =
            "id, center_id, date_jour, salle_id, creneau_id, infirmier_id, remplace_infirmier_id";

    private static final RowMapper<RemplacementInfirmier> MAPPER = (rs, i) -> new RemplacementInfirmier(
            rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class), rs.getDate("date_jour").toLocalDate(),
            rs.getObject("salle_id", UUID.class), rs.getObject("creneau_id", UUID.class),
            rs.getObject("infirmier_id", UUID.class), rs.getObject("remplace_infirmier_id", UUID.class));

    private final JdbcTemplate jdbc;

    public RemplacementInfirmierJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RemplacementInfirmier save(RemplacementInfirmier r) {
        jdbc.update("INSERT INTO infirmier_remplacement (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.centerId(), Date.valueOf(r.date()), r.salleId(), r.creneauId(), r.infirmierId(),
                r.remplaceId());
        return r;
    }

    @Override
    public Optional<RemplacementInfirmier> findById(UUID centerId, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier_remplacement WHERE center_id = ? AND id = ?",
                MAPPER, centerId, id).stream().findFirst();
    }

    @Override
    public void delete(UUID centerId, UUID id) {
        jdbc.update("DELETE FROM infirmier_remplacement WHERE center_id = ? AND id = ?", centerId, id);
    }
}
