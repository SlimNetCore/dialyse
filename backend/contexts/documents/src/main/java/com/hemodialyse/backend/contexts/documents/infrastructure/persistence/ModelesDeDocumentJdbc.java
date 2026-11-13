package com.hemodialyse.backend.contexts.documents.infrastructure.persistence;

import com.hemodialyse.backend.contexts.documents.application.ModeleEnDoublon;
import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.DefinitionDeModele;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCatalogue;
import com.hemodialyse.backend.contexts.documents.domain.RechercheDeModeles;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
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

    private static final String COLONNES_DU_CATALOGUE = "id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description, active";

    private static final RowMapper<ModeleDuCatalogue> LIGNE_DU_CATALOGUE = (rs, i) -> new ModeleDuCatalogue(rs.getObject("id", UUID.class),
            rs.getObject("center_id", UUID.class), rs.getString("code"), rs.getString("libelle"), rs.getString("type_document"),
            rs.getString("chemin_jrxml"), rs.getString("format_impression"), rs.getString("description"), rs.getBoolean("active"));

    @Override
    public List<ModeleDuCatalogue> lister(UUID centre, String type) {
        String sql = "SELECT " + COLONNES_DU_CATALOGUE + " FROM modele_document WHERE center_id = ? "
                + (type != null ? "AND type_document = ? " : "") + "ORDER BY libelle";
        return type != null ? jdbc.query(sql, LIGNE_DU_CATALOGUE, centre, type) : jdbc.query(sql, LIGNE_DU_CATALOGUE, centre);
    }

    @Override
    public List<ModeleDuCatalogue> rechercher(RechercheDeModeles recherche) {
        String sql = "SELECT " + COLONNES_DU_CATALOGUE + " FROM modele_document WHERE center_id = ? "
                + (recherche.type() != null ? "AND type_document = ? " : "") + "ORDER BY libelle LIMIT ? OFFSET ?";
        return recherche.type() != null
                ? jdbc.query(sql, LIGNE_DU_CATALOGUE, recherche.centre(), recherche.type(), recherche.taille(), recherche.decalage())
                : jdbc.query(sql, LIGNE_DU_CATALOGUE, recherche.centre(), recherche.taille(), recherche.decalage());
    }

    @Override
    public void creer(UUID id, DefinitionDeModele m) {
        try {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description, "
                            + "active) VALUES (?,?,?,?,?,?,?,?,?)",
                    id, m.centre(), m.code(), m.libelle(), m.type(), m.gabarit().valeur(), m.format(), m.description(), m.actif());
        } catch (DuplicateKeyException e) {
            throw new ModeleEnDoublon("Un modèle porte déjà ce code dans le centre, ou cet identifiant est déjà attribué", e);
        }
    }

    @Override
    public void modifier(UUID id, DefinitionDeModele m) {
        try {
            jdbc.update("UPDATE modele_document SET code=?, libelle=?, type_document=?, chemin_jrxml=?, format_impression=?, description=?, active=? "
                            + "WHERE id=? AND center_id=?",
                    m.code(), m.libelle(), m.type(), m.gabarit().valeur(), m.format(), m.description(), m.actif(), id, m.centre());
        } catch (DuplicateKeyException e) {
            throw new ModeleEnDoublon("Un autre modèle du centre porte déjà ce code", e);
        }
    }

    @Override
    public void supprimer(UUID centre, UUID id) {
        jdbc.update("DELETE FROM modele_document WHERE id = ? AND center_id = ?", id, centre);
    }
}
