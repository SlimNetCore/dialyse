package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Référentiel des infirmiers sur la table {@code infirmier} (DDL dans {@code db/schema.sql}). Toutes les requêtes
 * sont bornées au centre, hors la recherche par compte utilisateur réservée à la synchronisation d'état.
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
    public Infirmier save(Infirmier i) {
        int maj = jdbc.update("UPDATE infirmier SET matricule = ?, nom = ?, prenom = ?, telephone = ?, "
                        + "qualification = ?, habilite_isolement = ?, actif = ?, user_id = ? WHERE id = ? AND center_id = ?",
                i.matricule(), i.nom(), i.prenom(), i.telephone(), i.qualification().name(), i.habiliteIsolement(),
                i.actif(), i.userId(), i.id(), i.centerId());
        if (maj == 0) {
            jdbc.update("INSERT INTO infirmier (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    i.id(), i.centerId(), i.matricule(), i.nom(), i.prenom(), i.telephone(), i.qualification().name(),
                    i.habiliteIsolement(), i.actif(), i.userId());
        }
        return i;
    }

    @Override
    public Optional<Infirmier> findById(UUID centerId, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? AND id = ?", MAPPER, centerId, id)
                .stream().findFirst();
    }

    @Override
    public Optional<Infirmier> findByMatricule(UUID centerId, String matricule) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? AND LOWER(matricule) = LOWER(?)",
                MAPPER, centerId, matricule).stream().findFirst();
    }

    @Override
    public Optional<Infirmier> findByUserId(UUID centerId, UUID userId) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? AND user_id = ?",
                MAPPER, centerId, userId).stream().findFirst();
    }

    @Override
    public List<Infirmier> findAllByUserId(UUID userId) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE user_id = ?", MAPPER, userId);
    }

    @Override
    public PagedResult<Infirmier> findPaged(UUID centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier WHERE center_id = ?", Long.class, centerId);
        List<Infirmier> items = jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? "
                        + "ORDER BY nom, prenom, matricule LIMIT ? OFFSET ?",
                MAPPER, centerId, size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }
}
