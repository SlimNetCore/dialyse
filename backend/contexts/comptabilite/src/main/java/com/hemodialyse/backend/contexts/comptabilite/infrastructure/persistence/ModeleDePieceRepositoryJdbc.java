package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePieceRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC C4 : accès isolé au centre sur les tables V33 existantes.
 */
public final class ModeleDePieceRepositoryJdbc implements ModeleDePieceRepository {

    private static final String COLONNES = "SELECT id, center_id, code, libelle, journal_code, actif FROM modeles_piece";
    private final JdbcTemplate jdbc;

    public ModeleDePieceRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageModeles lister(CentreId centre, boolean actifsSeulement, int page, int taille) {
        String filtre = " WHERE center_id = ?" + (actifsSeulement ? " AND actif = TRUE" : "");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM modeles_piece" + filtre, Long.class, centre.valeur());
        List<ModeleDePiece> items = jdbc.query(COLONNES + filtre + " ORDER BY code LIMIT ? OFFSET ?",
                (rs, row) -> lire(rs, row), centre.valeur(), taille, (long) page * taille);
        return new PageModeles(items, total == null ? 0 : total, page, taille);
    }

    @Override
    public Optional<ModeleDePiece> parId(CentreId centre, UUID id) {
        return jdbc.query(COLONNES + " WHERE center_id = ? AND id = ?", this::lire, centre.valeur(), id).stream().findFirst();
    }

    @Override
    public Optional<ModeleDePiece> parCode(CentreId centre, String code) {
        return jdbc.query(COLONNES + " WHERE center_id = ? AND code = ?", this::lire, centre.valeur(), code).stream().findFirst();
    }

    @Override
    public void enregistrer(ModeleDePiece modele) {
        int modifies = jdbc.update("UPDATE modeles_piece SET code = ?, libelle = ?, journal_code = ?, actif = ? "
                        + "WHERE center_id = ? AND id = ?", modele.code(), modele.libelle(), modele.journal().valeur(),
                modele.actif(), modele.centre().valeur(), modele.id());
        if (modifies == 0) {
            jdbc.update("INSERT INTO modeles_piece (id, center_id, code, libelle, journal_code, actif) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", modele.id(), modele.centre().valeur(), modele.code(),
                    modele.libelle(), modele.journal().valeur(), modele.actif());
        }
        jdbc.update("DELETE FROM modeles_piece_lignes WHERE modele_id = ? AND center_id = ?",
                modele.id(), modele.centre().valeur());
        for (int position = 0; position < modele.lignes().size(); position++) {
            ModeleDePiece.Ligne ligne = modele.lignes().get(position);
            jdbc.update("INSERT INTO modeles_piece_lignes (modele_id, center_id, position_ligne, sens, compte, libelle) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", modele.id(), modele.centre().valeur(), position,
                    ligne.sens().name(), ligne.compte(), ligne.libelle());
        }
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        jdbc.update("DELETE FROM modeles_piece_lignes WHERE modele_id = ? AND center_id = ?", id, centre.valeur());
        jdbc.update("DELETE FROM modeles_piece WHERE id = ? AND center_id = ?", id, centre.valeur());
    }

    private ModeleDePiece lire(ResultSet rs, int row) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centerId = rs.getObject("center_id", UUID.class);
        List<ModeleDePiece.Ligne> lignes = jdbc.query("SELECT sens, compte, libelle FROM modeles_piece_lignes "
                        + "WHERE modele_id = ? AND center_id = ? ORDER BY position_ligne",
                (ligne, index) -> new ModeleDePiece.Ligne(ModeleDePiece.Sens.valueOf(ligne.getString("sens")),
                        ligne.getString("compte"), ligne.getString("libelle")), id, centerId);
        return new ModeleDePiece(id, new CentreId(centerId), rs.getString("code"), rs.getString("libelle"),
                CodeJournal.de(rs.getString("journal_code")), rs.getBoolean("actif"), lignes);
    }
}
