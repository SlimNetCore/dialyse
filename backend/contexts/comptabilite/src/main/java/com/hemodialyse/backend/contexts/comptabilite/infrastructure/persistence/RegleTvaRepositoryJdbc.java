package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC des règles de TVA : table {@code tva_types} (propriété de la Comptabilité, ADR 0030), SQL portable H2 et
 * PostgreSQL (l'identifiant vient de la règle, KB-CPT-21). Le taux est arrondi par la colonne {@code numeric(5,2)}
 * (KB-CPT-17, reproduit).
 */
public class RegleTvaRepositoryJdbc implements RegleTvaRepository {

    private static final String COLONNES = "id, libelle, type_prestation, taux, exonere, date_debut_validite, "
            + "date_fin_validite, texte_reference, actif";

    private final JdbcTemplate jdbc;

    public RegleTvaRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static RegleTva versLeDomaine(CentreId centre, ResultSet rs) throws SQLException {
        return RegleTva.reconstituee(new RegleTvaId(rs.getObject("id", UUID.class)), centre, rs.getString("libelle"),
                rs.getString("type_prestation"), rs.getBigDecimal("taux"), rs.getBoolean("exonere"),
                rs.getObject("date_debut_validite", LocalDate.class), rs.getObject("date_fin_validite", LocalDate.class),
                rs.getString("texte_reference"), rs.getBoolean("actif"));
    }

    @Override
    public List<RegleTva> duCentre(CentreId centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM tva_types WHERE center_id = ? ORDER BY date_debut_validite DESC",
                (rs, ligne) -> versLeDomaine(centre, rs), centre.valeur());
    }

    @Override
    public Optional<RegleTva> duCentre(CentreId centre, RegleTvaId id) {
        return jdbc.query("SELECT " + COLONNES + " FROM tva_types WHERE center_id = ? AND id = ?",
                (rs, ligne) -> versLeDomaine(centre, rs), centre.valeur(), id.valeur()).stream().findFirst();
    }

    @Override
    public void ajouter(RegleTva regle, String auteur) {
        jdbc.update("""
                        INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere,
                                               date_debut_validite, date_fin_validite, texte_reference,
                                               actif, created_at, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, ?)
                        """,
                regle.id().valeur(),
                regle.centre().valeur(),
                regle.libelle(),
                regle.taux().valeur(),
                regle.typePrestation(),
                regle.exonere(),
                regle.debutValidite(),
                regle.finValidite(),
                regle.texteReference(),
                auteur);
    }

    @Override
    public void remplacer(RegleTva regle) {
        jdbc.update("""
                        UPDATE tva_types
                        SET libelle = ?, taux = ?, type_prestation = ?, exonere = ?,
                            date_debut_validite = ?, date_fin_validite = ?, texte_reference = ?
                        WHERE id = ? AND center_id = ?
                        """,
                regle.libelle(), regle.taux().valeur(), regle.typePrestation(), regle.exonere(),
                regle.debutValidite(), regle.finValidite(), regle.texteReference(),
                regle.id().valeur(), regle.centre().valeur());
    }

    @Override
    public void desactiver(CentreId centre, RegleTvaId id) {
        jdbc.update("UPDATE tva_types SET actif = FALSE WHERE id = ? AND center_id = ?", id.valeur(), centre.valeur());
    }
}
