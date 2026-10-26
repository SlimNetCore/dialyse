package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des infirmiers sur la table {@code infirmier} (DDL dans {@code db/schema.sql}), pour les slices P3 et P4. Toutes les
 * requêtes sont bornées au centre. Les écritures sont servies par le contexte Planification (slice P2).
 */
@Component
public class InfirmierJdbcAdapter implements InfirmierRepositoryPort {

    private static final String COLONNES =
            "id, center_id, matricule, nom, prenom, telephone, qualification, habilite_isolement, actif, user_id";

    private static final RowMapper<Infirmier> MAPPER = (rs, i) -> new Infirmier(
            rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class), rs.getString("matricule"),
            rs.getString("nom"), rs.getString("prenom"), rs.getString("telephone"),
            QualificationInfirmier.valueOf(rs.getString("qualification")), rs.getBoolean("habilite_isolement"),
            rs.getBoolean("actif"), rs.getObject("user_id", UUID.class));

    private final JdbcTemplate jdbc;

    public InfirmierJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Infirmier> findById(UUID centerId, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? AND id = ?", MAPPER, centerId, id)
                .stream().findFirst();
    }

    @Override
    public Optional<Infirmier> findByUserId(UUID centerId, UUID userId) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? AND user_id = ?",
                MAPPER, centerId, userId).stream().findFirst();
    }
}
