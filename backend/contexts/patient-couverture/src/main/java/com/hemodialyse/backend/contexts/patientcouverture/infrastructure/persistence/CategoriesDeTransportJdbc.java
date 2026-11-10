package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CategoriesDeTransport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/** Les catégories de transport sur la table actuelle {@code categorie_transport} (lecture seule : aucun écrivain, KB-RF1-02). */
public class CategoriesDeTransportJdbc implements CategoriesDeTransport {

    private final JdbcTemplate jdbc;

    public CategoriesDeTransportJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CategorieDeTransportPubliee> lister(UUID centre) {
        return jdbc.query("SELECT id, libelle FROM categorie_transport WHERE center_id = ? ORDER BY libelle, id",
                (rs, n) -> new CategorieDeTransportPubliee(rs.getObject("id", UUID.class), rs.getString("libelle")), centre);
    }
}
