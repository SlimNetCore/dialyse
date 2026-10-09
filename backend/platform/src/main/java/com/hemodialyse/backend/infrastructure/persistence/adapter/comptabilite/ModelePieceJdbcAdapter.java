package com.hemodialyse.backend.infrastructure.persistence.adapter.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.port.ModelePieceRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.SensEcriture;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Modèles de pièces d'un centre (tables {@code modeles_piece} et {@code modeles_piece_lignes}). Chaque requête est
 * bornée au {@code center_id}.
 */
@Component
public class ModelePieceJdbcAdapter implements ModelePieceRepositoryPort {

    private static final String COLONNES = "SELECT id, center_id, code, libelle, journal_code, actif FROM modeles_piece";

    private final JdbcTemplate jdbc;

    public ModelePieceJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private ModelePiece lire(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centerId = rs.getObject("center_id", UUID.class);
        List<ModelePiece.Ligne> lignes = jdbc.query("SELECT sens, compte, libelle FROM modeles_piece_lignes "
                        + "WHERE modele_id = ? AND center_id = ? ORDER BY position_ligne",
                (l, i) -> new ModelePiece.Ligne(SensEcriture.valueOf(l.getString("sens")), l.getString("compte"),
                        l.getString("libelle")), id, centerId);
        return new ModelePiece(id, centerId, rs.getString("code"), rs.getString("libelle"),
                JournalCode.de(rs.getString("journal_code")), rs.getBoolean("actif"), lignes);
    }

    @Override
    public PagedResult<ModelePiece> findPaged(UUID centerId, boolean actifsSeulement, int page, int size) {
        String filtre = " WHERE center_id = ?" + (actifsSeulement ? " AND actif = TRUE" : "");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM modeles_piece" + filtre, Long.class, centerId);
        List<ModelePiece> items = jdbc.query(COLONNES + filtre + " ORDER BY code LIMIT ? OFFSET ?",
                (rs, i) -> lire(rs), centerId, size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public Optional<ModelePiece> findById(UUID centerId, UUID id) {
        return jdbc.query(COLONNES + " WHERE center_id = ? AND id = ?", (rs, i) -> lire(rs), centerId, id)
                .stream().findFirst();
    }

    @Override
    public Optional<ModelePiece> findByCode(UUID centerId, String code) {
        return jdbc.query(COLONNES + " WHERE center_id = ? AND code = ?", (rs, i) -> lire(rs), centerId, code)
                .stream().findFirst();
    }

    @Override
    public void save(ModelePiece m) {
        int modifies = jdbc.update("UPDATE modeles_piece SET code = ?, libelle = ?, journal_code = ?, actif = ? "
                        + "WHERE center_id = ? AND id = ?", m.code(), m.libelle(), m.journal().valeur(), m.actif(),
                m.centerId(), m.id());
        if (modifies == 0) {
            jdbc.update("INSERT INTO modeles_piece (id, center_id, code, libelle, journal_code, actif) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", m.id(), m.centerId(), m.code(), m.libelle(), m.journal().valeur(),
                    m.actif());
        }
        jdbc.update("DELETE FROM modeles_piece_lignes WHERE modele_id = ? AND center_id = ?", m.id(), m.centerId());
        for (int position = 0; position < m.lignes().size(); position++) {
            ModelePiece.Ligne ligne = m.lignes().get(position);
            jdbc.update("INSERT INTO modeles_piece_lignes (modele_id, center_id, position_ligne, sens, compte, libelle) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", m.id(), m.centerId(), position, ligne.sens().name(),
                    ligne.compte(), ligne.libelle());
        }
    }

    @Override
    public void delete(UUID centerId, UUID id) {
        jdbc.update("DELETE FROM modeles_piece_lignes WHERE modele_id = ? AND center_id = ?", id, centerId);
        jdbc.update("DELETE FROM modeles_piece WHERE id = ? AND center_id = ?", id, centerId);
    }

    @Override
    public boolean existsByCompte(UUID centerId, String compte) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM modeles_piece_lignes WHERE center_id = ? AND compte = ?",
                Long.class, centerId, compte);
        return total != null && total > 0;
    }

    @Override
    public boolean existsByJournal(UUID centerId, JournalCode journal) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM modeles_piece WHERE center_id = ? AND journal_code = ?",
                Long.class, centerId, journal.valeur());
        return total != null && total > 0;
    }
}
