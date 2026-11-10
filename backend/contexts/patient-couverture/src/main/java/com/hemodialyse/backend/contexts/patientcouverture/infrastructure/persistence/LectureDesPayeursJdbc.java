package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.application.UsageDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.FicheDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.PageDePayeurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lecture de l'administration des payeurs et usage des payeurs sur les tables actuelles (RF3a). La page joint le parent du
 * même centre (libellé « code · nom ») ; la recherche est sans casse, jokers pris littéralement. L'usage compte par identité de
 * payeur, comme avant la refonte (agences d'une caisse, centres payeurs d'une agence, patients d'un centre payeur).
 */
public class LectureDesPayeursJdbc implements LectureDesPayeurs, UsageDesPayeurs {

    private final JdbcTemplate jdbc;

    public LectureDesPayeursJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageDePayeurs page(CentreId centre, NiveauDePayeur niveau, String recherche, int page, int taille) {
        String table;
        String jointure;
        String colonnes;
        List<String> recherchees;
        switch (niveau) {
            case CAISSE -> {
                table = "caisse_assurance";
                jointure = "";
                colonnes = "t.id, t.code, t.nom, t.type_caisse, NULL AS adresse, NULL AS parent_id, NULL AS parent_libelle";
                recherchees = List.of("t.code", "t.nom");
            }
            case AGENCE -> {
                table = "agence";
                jointure = " LEFT JOIN caisse_assurance r ON r.id = t.caisse_id AND r.center_id = t.center_id";
                colonnes = "t.id, t.code, t.nom, NULL AS type_caisse, NULL AS adresse, t.caisse_id AS parent_id, "
                        + "CONCAT(r.code, ' · ', r.nom) AS parent_libelle";
                recherchees = List.of("t.code", "t.nom", "r.code", "r.nom");
            }
            default -> {
                table = "centre_payeur";
                jointure = " LEFT JOIN agence r ON r.id = t.agence_id AND r.center_id = t.center_id";
                colonnes = "t.id, t.code, t.nom, NULL AS type_caisse, t.adresse, t.agence_id AS parent_id, "
                        + "CONCAT(r.code, ' · ', r.nom) AS parent_libelle";
                recherchees = List.of("t.code", "t.nom", "t.adresse", "r.code", "r.nom");
            }
        }
        List<Object> parametres = new ArrayList<>();
        parametres.add(centre.valeur());
        String condition = " WHERE t.center_id = ?";
        if (recherche != null && !recherche.isBlank()) {
            String like = "%" + recherche.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<String> filtres = new ArrayList<>();
            for (String colonne : recherchees) {
                filtres.add("LOWER(COALESCE(" + colonne + ", '')) LIKE ? ESCAPE '\\'");
                parametres.add(like);
            }
            condition += " AND (" + String.join(" OR ", filtres) + ")";
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " t" + jointure + condition, Long.class,
                parametres.toArray());
        List<Object> pageParametres = new ArrayList<>(parametres);
        pageParametres.add(taille);
        pageParametres.add((long) page * taille);
        List<FicheDePayeur> lignes = jdbc.query("SELECT " + colonnes + " FROM " + table + " t" + jointure + condition
                        + " ORDER BY t.code, t.id LIMIT ? OFFSET ?",
                (rs, n) -> new FicheDePayeur(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getString("type_caisse"), rs.getString("adresse"), rs.getObject("parent_id", UUID.class),
                        rs.getString("parent_libelle")),
                pageParametres.toArray());
        return new PageDePayeurs(lignes, total == null ? 0 : total, page, taille);
    }

    @Override
    public long nombreDeReferences(NiveauDePayeur niveau, UUID id) {
        String sql = switch (niveau) {
            case CAISSE -> "SELECT COUNT(*) FROM agence WHERE caisse_id = ?";
            case AGENCE -> "SELECT COUNT(*) FROM centre_payeur WHERE agence_id = ?";
            case CENTRE_PAYEUR -> "SELECT COUNT(*) FROM patients WHERE centre_payeur_id = ?";
        };
        Long nombre = jdbc.queryForObject(sql, Long.class, id);
        return nombre == null ? 0 : nombre;
    }
}
