package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.UsagesDuParametrageJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des usages du paramétrage exécuté contre l'adaptateur JDBC sur H2 (mode PostgreSQL). Les tables des autres slices
 * sont créées ici avec les colonnes des migrations Flyway (en production, elles viennent d'elles). Variante PostgreSQL 16 :
 * {@code UsagesPostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:comptabilite-contrat-usages;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UsagesJdbcContratTest extends UsagesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private UsagesDuParametrage adaptateur;

    @BeforeEach
    void creerLesTablesEtLAdaptateur() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS mapping_comptable (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "compte_ventes VARCHAR(20) NOT NULL, compte_client_patient VARCHAR(20) NOT NULL, "
                + "compte_client_autre VARCHAR(20) NOT NULL, compte_banque VARCHAR(20) NOT NULL, "
                + "compte_caisse VARCHAR(20) NOT NULL, compte_tva_collectee VARCHAR(20), compte_stock VARCHAR(20), "
                + "compte_consommation VARCHAR(20), compte_factures_non_parvenues VARCHAR(20), "
                + "compte_boni_inventaire VARCHAR(20), compte_mali_inventaire VARCHAR(20), journal_vente VARCHAR(10), "
                + "journal_reglement_banque VARCHAR(10), journal_reglement_caisse VARCHAR(10), "
                + "journal_stock_reception VARCHAR(10), journal_stock_sortie VARCHAR(10), journal_stock_inventaire VARCHAR(10))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS modeles_piece (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(20) NOT NULL, libelle VARCHAR(100) NOT NULL, journal_code VARCHAR(10) NOT NULL, "
                + "actif BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS modeles_piece_lignes (modele_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "position_ligne INTEGER NOT NULL, sens VARCHAR(6) NOT NULL, compte VARCHAR(20) NOT NULL, "
                + "libelle VARCHAR(100), PRIMARY KEY (modele_id, position_ligne))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS articles (id UUID PRIMARY KEY, active BOOLEAN NOT NULL, center_id UUID NOT NULL, "
                + "code VARCHAR(255) NOT NULL, gere_par_lot BOOLEAN NOT NULL, libelle VARCHAR(255) NOT NULL, "
                + "seuil_alerte DECIMAL(38,2) NOT NULL, stock_quantity DECIMAL(38,2) NOT NULL, unite VARCHAR(255) NOT NULL, "
                + "compte_stock VARCHAR(20), compte_charge VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ecritures_comptables (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, date_ecriture DATE NOT NULL, date_piece DATE NOT NULL, "
                + "journal_code VARCHAR(10) NOT NULL, numero_piece VARCHAR(30) NOT NULL, statut VARCHAR(20) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS lignes_ecriture (id UUID PRIMARY KEY, ecriture_id UUID NOT NULL, "
                + "compte_scf VARCHAR(20) NOT NULL)");
        for (String table : new String[]{"lignes_ecriture", "ecritures_comptables", "articles", "modeles_piece_lignes",
                "modeles_piece", "mapping_comptable", "comptes_payeurs"}) {
            jdbc.update("DELETE FROM " + table);
        }
        com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles catalogue =
                new com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles() {
                    public java.util.Optional<com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue> article(
                            java.util.UUID centre, java.util.UUID article) {
                        return java.util.Optional.empty();
                    }

                    public java.util.List<com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue> articles(
                            java.util.UUID centre) {
                        var rs = jdbc.queryForRowSet(
                                "SELECT id, code, libelle, unite, gere_par_lot, compte_stock, compte_charge FROM articles WHERE center_id = ?",
                                centre);
                        var liste = new java.util.ArrayList<com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue>();
                        while (rs.next()) {
                            liste.add(new com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue(
                                    java.util.UUID.fromString(rs.getString("id")), centre,
                                    rs.getString("code"), rs.getString("libelle"), rs.getString("unite"), true, null, null, null,
                                    rs.getBoolean("gere_par_lot"), rs.getString("compte_stock"), rs.getString("compte_charge")));
                        }
                        return liste;
                    }

                    public java.util.List<com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue> articlesDeTraitement(
                            java.util.UUID centre, String typeTraitementAnemie) {
                        return java.util.List.of();
                    }

                    public java.math.BigDecimal quantitePourDose(java.util.UUID centre, java.util.UUID article,
                                                                 java.math.BigDecimal dose, String uniteDose) {
                        return java.math.BigDecimal.ZERO;
                    }
                };
        adaptateur = new UsagesDuParametrageJdbc(jdbc, catalogue);
    }

    @Override
    protected UsagesDuParametrage usages() {
        return adaptateur;
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
