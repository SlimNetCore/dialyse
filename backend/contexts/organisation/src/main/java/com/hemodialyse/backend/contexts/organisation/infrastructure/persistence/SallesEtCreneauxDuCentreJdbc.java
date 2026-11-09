package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Salles et créneaux d'un centre pour la Planification (lecture seule). SQL repris à l'identique des lecteurs retirés (O4 lot D,
 * ADR 0110) : {@code SallesDuCentreJdbc}, {@code VueDesSallesLegacy}, {@code LectureDesRessourcesLegacy}, {@code CreneauxDuCentreJdbc}
 * et {@code SeriesDuCentreJdbc} de la Planification. {@code salle.isolement} vaut {@code 'OUI'} ou {@code 'NON'} ; l'écriture est
 * {@link IsolementDesSallesJdbc}.
 */
public class SallesEtCreneauxDuCentreJdbc implements SallesEtCreneauxDuCentre {

    private static final String OUI = "OUI";

    private static final String COLONNES = "id, code, nom, isolement, capacite";

    private static final RowMapper<SalleDuCentre> SALLE = (rs, i) -> new SalleDuCentre(rs.getObject("id", UUID.class),
            rs.getString("code"), rs.getString("nom"), OUI.equals(rs.getString("isolement")),
            rs.getObject("capacite") == null ? null : rs.getInt("capacite"));

    private final JdbcTemplate jdbc;

    public SallesEtCreneauxDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SalleDuCentre> sallesParCode(UUID centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM salle WHERE center_id = ? ORDER BY code", SALLE, centre);
    }

    @Override
    public PageDeSalles pageDesSalles(UUID centre, int taille, long decalage) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM salle WHERE center_id = ?", Long.class, centre);
        List<SalleDuCentre> salles = jdbc.query("SELECT " + COLONNES + " FROM salle WHERE center_id = ? ORDER BY code LIMIT ? OFFSET ?",
                SALLE, centre, taille, decalage);
        return new PageDeSalles(salles, total == null ? 0 : total);
    }

    @Override
    public Optional<SalleDuCentre> salleDuCentre(UUID centre, UUID salle) {
        return jdbc.query("SELECT " + COLONNES + " FROM salle WHERE center_id = ? AND id = ?", SALLE, centre, salle).stream().findFirst();
    }

    @Override
    public List<CreneauDuCentre> creneauxParCode(UUID centre) {
        return jdbc.query("SELECT id, libelle FROM position_creneau WHERE center_id = ? ORDER BY code",
                (rs, i) -> new CreneauDuCentre(rs.getObject("id", UUID.class), rs.getString("libelle")), centre);
    }

    @Override
    public boolean contientLeCreneau(UUID centre, UUID creneau) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM position_creneau WHERE center_id = ? AND id = ?", Long.class, centre, creneau);
        return n != null && n > 0;
    }

    @Override
    public int nombreDeCreneaux(UUID centre) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM position_creneau WHERE center_id = ?", Integer.class, centre);
        return n == null ? 0 : n;
    }
}
