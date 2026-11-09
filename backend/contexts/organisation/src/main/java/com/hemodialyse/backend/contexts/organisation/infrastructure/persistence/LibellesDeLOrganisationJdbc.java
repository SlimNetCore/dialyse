package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Libellés de l'Organisation sur {@code salle}, {@code position_creneau} et {@code medecin}. SQL repris à l'identique des lecteurs retirés
 * (O4 lot C, ADR 0109) :
 * <ul>
 *   <li>{@code LecturesPublieesDesSeancesJdbc} (Séance) : {@code code} de {@code salle} et {@code code, libelle} de {@code position_creneau},
 *   bornés au centre ;</li>
 *   <li>{@code LectureDesMouvementsJdbc} (Patient et couverture) : {@code salle.nom} et {@code position_creneau.libelle}, jointures
 *   {@code LEFT JOIN … ON id} SANS borne de centre ;</li>
 *   <li>{@code LectureDesPatientsJdbc} (Patient et couverture) : libellé du médecin, code et libellé du créneau, et les recherches par
 *   libellé ou identifiant, tous bornés au centre.</li>
 * </ul>
 * Un lot vide n'ouvre aucune requête.
 */
public class LibellesDeLOrganisationJdbc implements LibellesDeLOrganisation {

    private static final String MEDECIN_LIBELLE = "TRIM(COALESCE(nom, '') || ' ' || COALESCE(prenom, ''))";
    private static final String MEDECIN_RECHERCHE =
            "LOWER(COALESCE(nom, '') || ' ' || COALESCE(prenom, '') || ' ' || COALESCE(prenom, '') || ' ' || COALESCE(nom, ''))";
    private static final String CODE_LIBELLE_RECHERCHE = "LOWER(COALESCE(code, '') || ' ' || COALESCE(libelle, ''))";

    private final NamedParameterJdbcTemplate nomme;

    public LibellesDeLOrganisationJdbc(JdbcTemplate jdbc) {
        this.nomme = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public Map<UUID, String> codesDesSalles(UUID centre, Collection<UUID> salles) {
        Set<UUID> ids = distincts(salles);
        Map<UUID, String> sortie = new HashMap<>();
        if (ids.isEmpty()) return sortie;
        nomme.query("SELECT id, code FROM salle WHERE center_id = :centre AND id IN (:ids)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("ids", ids), rs -> {
                    String code = rs.getString("code");
                    if (code != null) sortie.put(rs.getObject("id", UUID.class), code);
                });
        return sortie;
    }

    @Override
    public Map<UUID, LibelleDeCreneau> creneauxDuCentre(UUID centre, Collection<UUID> creneaux) {
        Set<UUID> ids = distincts(creneaux);
        Map<UUID, LibelleDeCreneau> sortie = new HashMap<>();
        if (ids.isEmpty()) return sortie;
        nomme.query("SELECT id, code, libelle FROM position_creneau WHERE center_id = :centre AND id IN (:ids)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("ids", ids),
                rs -> {
                    sortie.put(rs.getObject("id", UUID.class), new LibelleDeCreneau(rs.getString("code"), rs.getString("libelle")));
                });
        return sortie;
    }

    @Override
    public Map<UUID, String> nomsDesSalles(Collection<UUID> salles) {
        Set<UUID> ids = distincts(salles);
        Map<UUID, String> sortie = new HashMap<>();
        if (ids.isEmpty()) return sortie;
        nomme.query("SELECT id, nom FROM salle WHERE id IN (:ids)", new MapSqlParameterSource().addValue("ids", ids), rs -> {
            String nom = rs.getString("nom");
            if (nom != null) sortie.put(rs.getObject("id", UUID.class), nom);
        });
        return sortie;
    }

    @Override
    public Map<UUID, String> libellesDesCreneaux(Collection<UUID> creneaux) {
        Set<UUID> ids = distincts(creneaux);
        Map<UUID, String> sortie = new HashMap<>();
        if (ids.isEmpty()) return sortie;
        nomme.query("SELECT id, libelle FROM position_creneau WHERE id IN (:ids)", new MapSqlParameterSource().addValue("ids", ids), rs -> {
            String libelle = rs.getString("libelle");
            if (libelle != null) sortie.put(rs.getObject("id", UUID.class), libelle);
        });
        return sortie;
    }

    @Override
    public Map<UUID, String> nomsDesMedecins(UUID centre, Collection<UUID> medecins) {
        Set<UUID> ids = distincts(medecins);
        Map<UUID, String> sortie = new HashMap<>();
        if (ids.isEmpty()) return sortie;
        nomme.query("SELECT id, " + MEDECIN_LIBELLE + " AS label FROM medecin WHERE center_id = :centre AND id IN (:ids)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("ids", ids), rs -> {
                    String libelle = rs.getString("label");
                    if (libelle != null && !libelle.isBlank()) sortie.put(rs.getObject("id", UUID.class), libelle.trim());
                });
        return sortie;
    }

    @Override
    public Set<UUID> medecinsCorrespondant(UUID centre, String terme) {
        return correspondances("medecin", MEDECIN_RECHERCHE, centre, terme);
    }

    @Override
    public Set<UUID> creneauxCorrespondant(UUID centre, String terme) {
        return correspondances("position_creneau", CODE_LIBELLE_RECHERCHE, centre, terme);
    }

    private Set<UUID> correspondances(String table, String expression, UUID centre, String terme) {
        if (terme == null) return new HashSet<>();
        String like = "%" + terme.trim().toLowerCase(Locale.ROOT) + "%";
        return new HashSet<>(nomme.queryForList("SELECT id FROM " + table + " WHERE center_id = :centre AND (" + expression
                        + " LIKE :terme OR LOWER(CAST(id AS VARCHAR(36))) LIKE :terme)",
                new MapSqlParameterSource().addValue("centre", centre).addValue("terme", like), UUID.class));
    }

    private static Set<UUID> distincts(Collection<UUID> ids) {
        Set<UUID> sortie = new HashSet<>();
        if (ids != null) ids.stream().filter(Objects::nonNull).forEach(sortie::add);
        return sortie;
    }
}
