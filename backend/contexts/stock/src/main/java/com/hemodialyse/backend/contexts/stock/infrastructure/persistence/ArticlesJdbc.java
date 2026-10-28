package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ConditionConservation;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.PageDArticles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Pagination;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Table {@code articles} (même schéma que le legacy). L'insertion pose le stock et le PMP à 0 ; la mise à jour n'écrit que la
 * fiche et l'activité : le stock et le PMP, écrits par les mouvements (legacy jusqu'aux slices S5 à S7), ne sont jamais
 * réécrits par le référentiel (le legacy les réécrivait avec la valeur lue avant la modification).
 */
public class ArticlesJdbc implements Articles {

    private static final String COLONNES = "id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
            + "gere_par_lot, active, type_traitement_anemie, created_at, dci, forme_galenique, code_barres, reference_fabricant, "
            + "unite_achat, coefficient_achat, dosage_par_unite, unite_dosage, fournisseur_id, tva_type_id, prix_achat, stock_max, "
            + "peremption_obligatoire, condition_conservation, produit_dangereux, dechet_dasri, compte_stock, compte_charge";

    private static final RowMapper<Article> ARTICLE = (rs, i) -> {
        String condition = rs.getString("condition_conservation");
        String type = rs.getString("type_traitement_anemie");
        FicheArticle fiche = new FicheArticle(rs.getString("code"), rs.getString("libelle"), rs.getString("dci"),
                rs.getString("forme_galenique"), rs.getString("code_barres"), rs.getString("reference_fabricant"),
                rs.getString("unite"), rs.getString("unite_achat"), rs.getBigDecimal("coefficient_achat"),
                rs.getBigDecimal("dosage_par_unite"), rs.getString("unite_dosage"), rs.getObject("fournisseur_id", UUID.class),
                rs.getObject("tva_type_id", UUID.class), rs.getBigDecimal("prix_achat"), rs.getBigDecimal("seuil_alerte"),
                rs.getBigDecimal("stock_max"), rs.getBoolean("gere_par_lot"), rs.getBoolean("peremption_obligatoire"),
                condition == null ? null : ConditionConservation.valueOf(condition), rs.getBoolean("produit_dangereux"),
                rs.getBoolean("dechet_dasri"), type == null ? null : TypeTraitementAnemie.valueOf(type),
                rs.getString("compte_stock"), rs.getString("compte_charge"));
        return Article.reconstituer(rs.getObject("id", UUID.class), new CentreId(rs.getObject("center_id", UUID.class)), fiche,
                rs.getBoolean("active"), rs.getBigDecimal("stock_quantity"), rs.getBigDecimal("pmp_courant"),
                rs.getObject("created_at", OffsetDateTime.class));
    };

    private final JdbcTemplate jdbc;

    public ArticlesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String nom(Enum<?> valeur) {
        return valeur == null ? null : valeur.name();
    }

    @Override
    public Optional<Article> parId(CentreId centre, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM articles WHERE id = ? AND center_id = ?", ARTICLE, id, centre.valeur())
                .stream().findFirst();
    }

    @Override
    public Optional<Article> parCode(CentreId centre, String code) {
        return jdbc.query("SELECT " + COLONNES + " FROM articles WHERE center_id = ? AND UPPER(code) = UPPER(?) ORDER BY id",
                ARTICLE, centre.valeur(), code).stream().findFirst();
    }

    @Override
    public void enregistrer(Article article) {
        FicheArticle f = article.fiche();
        int modifies = jdbc.update("UPDATE articles SET code = ?, libelle = ?, unite = ?, seuil_alerte = ?, gere_par_lot = ?, "
                        + "active = ?, type_traitement_anemie = ?, dci = ?, forme_galenique = ?, code_barres = ?, "
                        + "reference_fabricant = ?, unite_achat = ?, coefficient_achat = ?, dosage_par_unite = ?, unite_dosage = ?, "
                        + "fournisseur_id = ?, tva_type_id = ?, prix_achat = ?, stock_max = ?, peremption_obligatoire = ?, "
                        + "condition_conservation = ?, produit_dangereux = ?, dechet_dasri = ?, compte_stock = ?, compte_charge = ? "
                        + "WHERE id = ? AND center_id = ?",
                f.code(), f.libelle(), f.unite(), f.seuilAlerte(), f.gereParLot(), article.actif(), nom(f.typeTraitementAnemie()),
                f.dci(), f.formeGalenique(), f.codeBarres(), f.referenceFabricant(), f.uniteAchat(), f.coefficientAchat(),
                f.dosageParUnite(), f.uniteDosage(), f.fournisseurId(), f.tvaTypeId(), f.prixAchat(), f.stockMax(),
                f.peremptionObligatoire(), nom(f.conditionConservation()), f.produitDangereux(), f.dechetDasri(),
                f.compteStock(), f.compteCharge(), article.id(), article.centre().valeur());
        if (modifies == 0) {
            jdbc.update("INSERT INTO articles (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    article.id(), article.centre().valeur(), f.code(), f.libelle(), f.unite(), article.stock(), f.seuilAlerte(),
                    article.pmp(), f.gereParLot(), article.actif(), nom(f.typeTraitementAnemie()), article.creeLe(), f.dci(),
                    f.formeGalenique(), f.codeBarres(), f.referenceFabricant(), f.uniteAchat(), f.coefficientAchat(),
                    f.dosageParUnite(), f.uniteDosage(), f.fournisseurId(), f.tvaTypeId(), f.prixAchat(), f.stockMax(),
                    f.peremptionObligatoire(), nom(f.conditionConservation()), f.produitDangereux(), f.dechetDasri(),
                    f.compteStock(), f.compteCharge());
        }
    }

    @Override
    public PageDArticles rechercher(CentreId centre, String texte, Boolean actif, Pagination pagination) {
        StringBuilder where = new StringBuilder(" WHERE center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        if (actif != null) {
            where.append(" AND active = ?");
            parametres.add(actif);
        }
        if (texte != null && !texte.isBlank()) {
            // comme le legacy : texte nettoyé, en minuscules, jokers SQL non échappés
            String like = "%" + texte.trim().toLowerCase(Locale.ROOT) + "%";
            where.append(" AND (LOWER(code) LIKE ? OR LOWER(libelle) LIKE ? OR LOWER(COALESCE(dci, '')) LIKE ?"
                    + " OR LOWER(COALESCE(code_barres, '')) LIKE ?)");
            parametres.addAll(List.of(like, like, like, like));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM articles" + where, Long.class, parametres.toArray());
        List<Object> pagines = new ArrayList<>(parametres);
        pagines.add(pagination.taille());
        pagines.add(pagination.decalage());
        List<Article> page = jdbc.query("SELECT " + COLONNES + " FROM articles" + where + " ORDER BY code ASC LIMIT ? OFFSET ?",
                ARTICLE, pagines.toArray());
        return new PageDArticles(page, total == null ? 0 : total, pagination);
    }

    @Override
    public List<Article> tous(CentreId centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM articles WHERE center_id = ? ORDER BY code ASC", ARTICLE, centre.valeur());
    }

    @Override
    public List<Article> parTypeDeTraitement(CentreId centre, TypeTraitementAnemie type) {
        return jdbc.query("SELECT " + COLONNES + " FROM articles WHERE center_id = ? AND type_traitement_anemie = ? ORDER BY code ASC",
                ARTICLE, centre.valeur(), type.name());
    }

    @Override
    public java.util.Set<UUID> identifiants(CentreId centre) {
        return new java.util.HashSet<>(jdbc.queryForList("SELECT id FROM articles WHERE center_id = ?", UUID.class, centre.valeur()));
    }
}


