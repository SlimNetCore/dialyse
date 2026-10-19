package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.LectureDesTypesDeTva;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.TypeDeTvaVue;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture JDBC de l'écran « Types de TVA » sur {@code tva_types} : page triée par début décroissant puis libellé (comme le
 * legacy), avec les colonnes d'audit {@code created_at} (UTC) et {@code created_by}.
 */
public class TypesDeTvaJdbc implements LectureDesTypesDeTva {

    private static final String COLONNES = "id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, "
            + "date_fin_validite, texte_reference, actif, created_at, created_by";

    private final JdbcTemplate jdbc;

    public TypesDeTvaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static TypeDeTvaVue vue(ResultSet rs, int ligne) throws SQLException {
        Timestamp creeLe = rs.getTimestamp("created_at");
        return new TypeDeTvaVue(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class), rs.getString("libelle"),
                rs.getBigDecimal("taux"), rs.getString("type_prestation"), rs.getBoolean("exonere"),
                rs.getObject("date_debut_validite", LocalDate.class), rs.getObject("date_fin_validite", LocalDate.class),
                rs.getString("texte_reference"), rs.getBoolean("actif"),
                creeLe == null ? null : creeLe.toInstant().atOffset(ZoneOffset.UTC), rs.getString("created_by"));
    }

    @Override
    public PageDe<TypeDeTvaVue> page(CentreId centre, int page, int taille) {
        List<TypeDeTvaVue> items = jdbc.query("SELECT " + COLONNES + " FROM tva_types WHERE center_id = ? "
                        + "ORDER BY date_debut_validite DESC, libelle LIMIT ? OFFSET ?",
                TypesDeTvaJdbc::vue, centre.valeur(), taille, (long) page * taille);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM tva_types WHERE center_id = ?", Long.class, centre.valeur());
        return new PageDe<>(items, total == null ? 0 : total, page, taille);
    }

    @Override
    public Optional<TypeDeTvaVue> type(CentreId centre, RegleTvaId id) {
        return jdbc.query("SELECT " + COLONNES + " FROM tva_types WHERE center_id = ? AND id = ?", TypesDeTvaJdbc::vue,
                centre.valeur(), id.valeur()).stream().findFirst();
    }
}


