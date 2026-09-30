package com.hemodialyse.backend.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution des libellés des référentiels affichés dans la liste des patients (médecin traitant,
 * position/créneau, transporteurs, forfait) et recherche inverse « texte saisi → identifiants »
 * pour les filtres de colonne.
 * <p>
 * Ces tables ne sont pas des entités JPA (créées par {@code db/schema.sql}) : accès JDBC, toujours
 * restreint au centre courant (isolation multi-centre), et par lot pour éviter le N+1.
 */
@Component
public class PatientReferenceLookup {

    /**
     * Libellé du médecin : « NOM Prénom ».
     */
    private static final String MEDECIN_LABEL = "TRIM(COALESCE(nom, '') || ' ' || COALESCE(prenom, ''))";
    /**
     * Recherche médecin : NOM Prénom ou Prénom NOM.
     */
    private static final String MEDECIN_SEARCH =
            "LOWER(COALESCE(nom, '') || ' ' || COALESCE(prenom, '') || ' ' || COALESCE(prenom, '') || ' ' || COALESCE(nom, ''))";
    private static final String CODE_LIBELLE_SEARCH = "LOWER(COALESCE(code, '') || ' ' || COALESCE(libelle, ''))";
    private static final String NOM_SEARCH = "LOWER(COALESCE(nom, ''))";

    private final NamedParameterJdbcTemplate jdbc;

    public PatientReferenceLookup(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static Set<UUID> distinctNonNull(Collection<UUID> ids) {
        Set<UUID> out = new HashSet<>();
        if (ids != null) ids.stream().filter(Objects::nonNull).forEach(out::add);
        return out;
    }

    public Map<UUID, String> medecinLabels(UUID centerId, Collection<UUID> ids) {
        return labels("SELECT id, " + MEDECIN_LABEL + " AS label FROM medecin", centerId, ids);
    }

    public Map<UUID, String> transporteurLabels(UUID centerId, Collection<UUID> ids) {
        return labels("SELECT id, nom AS label FROM transporteur", centerId, ids);
    }

    public Map<UUID, CodeLibelle> positions(UUID centerId, Collection<UUID> ids) {
        return codeLibelles("position_creneau", centerId, ids);
    }

    public Map<UUID, CodeLibelle> forfaits(UUID centerId, Collection<UUID> ids) {
        return codeLibelles("forfait", centerId, ids);
    }

    public Set<UUID> matchMedecins(UUID centerId, String term) {
        return match("medecin", MEDECIN_SEARCH, centerId, term);
    }

    public Set<UUID> matchPositions(UUID centerId, String term) {
        return match("position_creneau", CODE_LIBELLE_SEARCH, centerId, term);
    }

    public Set<UUID> matchTransporteurs(UUID centerId, String term) {
        return match("transporteur", NOM_SEARCH, centerId, term);
    }

    public Set<UUID> matchForfaits(UUID centerId, String term) {
        return match("forfait", CODE_LIBELLE_SEARCH, centerId, term);
    }

    private Map<UUID, String> labels(String select, UUID centerId, Collection<UUID> ids) {
        Set<UUID> distinct = distinctNonNull(ids);
        if (distinct.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>();
        jdbc.query(select + " WHERE center_id = :centerId AND id IN (:ids)",
                new MapSqlParameterSource().addValue("centerId", centerId).addValue("ids", distinct),
                rs -> {
                    String label = rs.getString("label");
                    if (label != null && !label.isBlank()) {
                        out.put(rs.getObject("id", UUID.class), label.trim());
                    }
                });
        return out;
    }

    private Map<UUID, CodeLibelle> codeLibelles(String table, UUID centerId, Collection<UUID> ids) {
        Set<UUID> distinct = distinctNonNull(ids);
        if (distinct.isEmpty()) return Map.of();
        Map<UUID, CodeLibelle> out = new HashMap<>();
        jdbc.query("SELECT id, code, libelle FROM " + table + " WHERE center_id = :centerId AND id IN (:ids)",
                new MapSqlParameterSource().addValue("centerId", centerId).addValue("ids", distinct),
                rs -> {
                    out.put(rs.getObject("id", UUID.class),
                            new CodeLibelle(rs.getString("code"), rs.getString("libelle")));
                });
        return out;
    }

    /**
     * Identifiants dont le libellé contient {@code term} (insensible à la casse). Pour ne pas casser
     * les vues enregistrées avant l'affichage des libellés, un fragment d'UUID est aussi accepté.
     */
    private Set<UUID> match(String table, String searchExpr, UUID centerId, String term) {
        String like = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM " + table + " WHERE center_id = :centerId AND ("
                        + searchExpr + " LIKE :term OR LOWER(CAST(id AS VARCHAR(36))) LIKE :term)",
                new MapSqlParameterSource().addValue("centerId", centerId).addValue("term", like),
                UUID.class);
        return new HashSet<>(ids);
    }

    /**
     * Code + libellé d'un référentiel (position/créneau, forfait).
     */
    public record CodeLibelle(String code, String libelle) {
    }
}

