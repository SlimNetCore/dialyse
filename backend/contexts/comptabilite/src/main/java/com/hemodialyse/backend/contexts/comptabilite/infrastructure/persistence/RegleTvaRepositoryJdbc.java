package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adapter JDBC des règles de TVA : table {@code tva_types}, partagée avec la Facturation (SQL direct : la Facturation y écrit
 * aussi ses types de TVA). L'identifiant de chaque ligne est généré en Java : la route fonctionne sur H2 comme sur PostgreSQL
 * (KB-CPT-21 corrigé : l'ancien {@code RANDOM_UUID()} n'existe que sur H2). Le taux est arrondi par la colonne
 * {@code numeric(5,2)} (KB-CPT-17, reproduit).
 */
public class RegleTvaRepositoryJdbc implements RegleTvaRepository {

    private static final String UTILISATEUR = "comptabilite";

    private final JdbcTemplate jdbc;

    public RegleTvaRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static RegleTva versLeDomaine(CentreId centre, ResultSet rs) throws SQLException {
        return RegleTva.reconstituee(centre, rs.getString("type_prestation"), rs.getBigDecimal("taux"), rs.getBoolean("exonere"),
                rs.getObject("date_debut_validite", LocalDate.class), rs.getObject("date_fin_validite", LocalDate.class),
                rs.getString("texte_reference"), rs.getBoolean("actif"));
    }

    @Override
    public List<RegleTva> duCentre(CentreId centre) {
        return jdbc.query("""
                        SELECT type_prestation, taux, exonere, date_debut_validite, date_fin_validite, texte_reference, actif
                        FROM tva_types
                        WHERE center_id = ?
                        ORDER BY date_debut_validite DESC
                        """,
                (rs, ligne) -> versLeDomaine(centre, rs), centre.valeur());
    }

    @Override
    public void ajouter(RegleTva regle) {
        jdbc.update("""
                        INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere,
                                               date_debut_validite, date_fin_validite, texte_reference,
                                               actif, created_at, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, ?)
                        """,
                UUID.randomUUID(),
                regle.centre().valeur(),
                regle.typePrestation() + " " + regle.taux() + "%",
                regle.taux().valeur(),
                regle.typePrestation(),
                regle.exonere(),
                regle.debutValidite(),
                regle.finValidite(),
                regle.texteReference(),
                UTILISATEUR);
    }
}
