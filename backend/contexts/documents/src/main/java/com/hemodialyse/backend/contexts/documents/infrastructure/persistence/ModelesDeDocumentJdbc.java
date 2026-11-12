package com.hemodialyse.backend.contexts.documents.infrastructure.persistence;

import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

/** Lecture de {@code modele_document} : SQL repris à l'identique de {@code DocumentRestController} (DOC1). */
public class ModelesDeDocumentJdbc implements ModelesDeDocument {

    private static final String COLONNES = "id, chemin_jrxml, format_impression, type_document";

    private static final RowMapper<ModeleDeDocument> LIGNE = (rs, i) -> {
        String chemin = rs.getString("chemin_jrxml");
        String format = rs.getString("format_impression");
        String type = rs.getString("type_document");
        return new ModeleDeDocument(rs.getObject("id", UUID.class), chemin == null ? "" : chemin, format == null ? "PDF" : format,
                type == null ? "CUSTOM" : type);
    };

    private final JdbcTemplate jdbc;

    public ModelesDeDocumentJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ModeleDeDocument> actifDuCentre(UUID centre, String typeNormalise) {
        return jdbc.query("SELECT " + COLONNES + " FROM modele_document WHERE center_id = ? AND UPPER(type_document) = ? AND active = TRUE "
                + "ORDER BY created_at DESC", LIGNE, centre, typeNormalise).stream().findFirst();
    }

    @Override
    public Optional<ModeleDeDocument> duCentre(UUID modele, UUID centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM modele_document WHERE id = ? AND center_id = ?", LIGNE, modele, centre).stream()
                .findFirst();
    }

    @Override
    public Optional<String> typeDuModele(UUID modele) {
        return jdbc.queryForList("SELECT type_document FROM modele_document WHERE id = ?", String.class, modele).stream()
                .filter(t -> t != null).findFirst();
    }
}
