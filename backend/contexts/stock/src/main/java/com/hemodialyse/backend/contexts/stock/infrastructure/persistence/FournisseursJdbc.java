package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Fournisseurs;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

/**
 * Table {@code fournisseurs} (même schéma que le legacy). La recherche reproduit celle du legacy (« contient, sans casse » de
 * Spring Data : jokers du texte échappés), triée par raison sociale.
 */
public class FournisseursJdbc implements Fournisseurs {

    private static final String COLONNES = "id, center_id, code, raison_sociale, contact, telephone, email, actif";
    private static final RowMapper<Fournisseur> FOURNISSEUR = (rs, i) -> new Fournisseur(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("raison_sociale"),
            rs.getString("contact"), rs.getString("telephone"), rs.getString("email"), rs.getBoolean("actif"));

    private final JdbcTemplate jdbc;

    public FournisseursJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String echappe(String texte) {
        return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public void enregistrer(Fournisseur f) {
        jdbc.update("INSERT INTO fournisseurs (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)", f.id(), f.centre().valeur(),
                f.code(), f.raisonSociale(), f.contact(), f.telephone(), f.email(), f.actif());
    }

    @Override
    public List<Fournisseur> actifs(CentreId centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM fournisseurs WHERE center_id = ? AND actif = TRUE ORDER BY raison_sociale ASC",
                FOURNISSEUR, centre.valeur());
    }

    @Override
    public boolean existe(CentreId centre, UUID id) {
        Integer nombre = jdbc.queryForObject("SELECT COUNT(*) FROM fournisseurs WHERE center_id = ? AND id = ?", Integer.class,
                centre.valeur(), id);
        return nombre != null && nombre > 0;
    }

    @Override
    public List<Fournisseur> chercher(CentreId centre, String texte) {
        return jdbc.query("SELECT " + COLONNES + " FROM fournisseurs WHERE center_id = ? AND actif = TRUE "
                        + "AND UPPER(raison_sociale) LIKE UPPER(?) ESCAPE '\\' ORDER BY raison_sociale ASC",
                FOURNISSEUR, centre.valeur(), "%" + echappe(texte) + "%");
    }
}


