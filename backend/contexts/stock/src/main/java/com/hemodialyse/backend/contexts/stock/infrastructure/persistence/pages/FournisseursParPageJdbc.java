package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs.PagesDesFournisseurs;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Page des fournisseurs actifs du centre en SQL (table {@code fournisseurs}) : {@code COUNT(*)} puis
 * {@code ORDER BY … LIMIT ? OFFSET ?} avec le même {@code WHERE}. Filtres « contient » insensibles à la casse (jokers échappés) ;
 * tri par table fixe, départage {@code id}.
 */
public class FournisseursParPageJdbc implements PagesDesFournisseurs {

    private static final String COLONNES = "id, center_id, code, raison_sociale, contact, telephone, email, actif";
    private static final Map<String, String> TRIS = Map.of(
            "code", "code",
            "raisonSociale", "raison_sociale",
            "contact", "contact");
    private static final RowMapper<Fournisseur> FOURNISSEUR = (rs, i) -> new Fournisseur(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("raison_sociale"),
            rs.getString("contact"), rs.getString("telephone"), rs.getString("email"), rs.getBoolean("actif"));
    private static final String CONTIENT = " LIKE ? ESCAPE '\\'";

    private final JdbcTemplate jdbc;

    public FournisseursParPageJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageDeResultats<Fournisseur> page(CentreId centre, FiltreDesFournisseurs filtre, DemandeDePage demande) {
        StringBuilder where = new StringBuilder(" WHERE center_id = ? AND actif = TRUE");
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        if (filtre.recherche() != null) {
            where.append(" AND (LOWER(code)").append(CONTIENT).append(" OR LOWER(raison_sociale)").append(CONTIENT)
                    .append(" OR LOWER(contact)").append(CONTIENT).append(')');
            String motif = SqlDePage.contient(filtre.recherche());
            parametres.addAll(List.of(motif, motif, motif));
        }
        if (filtre.code() != null) {
            where.append(" AND LOWER(code)").append(CONTIENT);
            parametres.add(SqlDePage.contient(filtre.code()));
        }
        if (filtre.raisonSociale() != null) {
            where.append(" AND LOWER(raison_sociale)").append(CONTIENT);
            parametres.add(SqlDePage.contient(filtre.raisonSociale()));
        }
        if (filtre.contact() != null) {
            where.append(" AND LOWER(contact)").append(CONTIENT);
            parametres.add(SqlDePage.contient(filtre.contact()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM fournisseurs" + where, Long.class, parametres.toArray());
        List<Object> pagines = new ArrayList<>(parametres);
        pagines.add(demande.taille());
        pagines.add(demande.decalage());
        List<Fournisseur> items = jdbc.query("SELECT " + COLONNES + " FROM fournisseurs" + where
                + SqlDePage.ordre(demande, TRIS, "id") + SqlDePage.limite(), FOURNISSEUR, pagines.toArray());
        return new PageDeResultats<>(items, total == null ? 0 : total, demande.page(), demande.taille());
    }
}
