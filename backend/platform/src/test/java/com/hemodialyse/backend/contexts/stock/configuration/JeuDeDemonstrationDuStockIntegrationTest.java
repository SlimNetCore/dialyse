package com.hemodialyse.backend.contexts.stock.configuration;

import com.hemodialyse.backend.contexts.stock.application.commandes.GestionDesBonsDeCommande;
import com.hemodialyse.backend.contexts.stock.application.reception.GestionDesReceptions;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesFournisseursEtEmplacements;
import com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Le jeu de démonstration du stock (désactivé en test) écrit par les cas d'usage du contexte Stock : référentiel, bon de commande
 * validé, deux réceptions (lots, PMP pondéré), une sortie FEFO ; idempotent. Les données créées dans le centre de démonstration
 * (partagé avec d'autres tests) sont retirées après le test, compteurs de pièces compris.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class JeuDeDemonstrationDuStockIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final List<String> CODES = List.of("DIA-FX60", "LIGNE-AV", "AIG-FAV", "HEP-5000", "SERPH-500", "BICA-650",
            "EPO-ALFA-4000", "FER-SACC-100");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReferentielDesFournisseursEtEmplacements referentiel;
    @Autowired
    private ReferentielDesArticles articles;
    @Autowired
    private GestionDesBonsDeCommande commandes;
    @Autowired
    private GestionDesReceptions receptions;
    @Autowired
    private GestionDesSorties sorties;

    private List<Map<String, Object>> compteurs;

    private ApplicationRunner seeder() {
        return new JeuDeDemonstrationDuStock().stockDemoDataRunner(referentiel, articles, commandes, receptions, sorties);
    }

    private BigDecimal valeur(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    @BeforeEach
    void memoriser() {
        assumeTrue(jdbc.queryForObject("SELECT COUNT(*) FROM fournisseurs WHERE center_id = ?", Integer.class, CENTRE) == 0,
                "le centre de démonstration a déjà des fournisseurs : le seeder ne ferait rien");
        compteurs = jdbc.queryForList("SELECT cle, prefixe, dernier_compteur FROM app_settings WHERE center_id = ?", CENTRE);
    }

    @AfterEach
    void retirer() {
        if (compteurs == null) {
            return;
        }
        String articlesSeed = "SELECT id FROM articles WHERE center_id = ? AND code IN ('" + String.join("','", CODES) + "')";
        jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ? "
                + "AND created_by = 'seed')", CENTRE);
        jdbc.update("DELETE FROM bons_sortie WHERE center_id = ? AND created_by = 'seed'", CENTRE);
        jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id IN (SELECT id FROM bons_reception WHERE center_id = ? "
                + "AND created_by = 'seed')", CENTRE);
        jdbc.update("DELETE FROM bons_reception WHERE center_id = ? AND created_by = 'seed'", CENTRE);
        jdbc.update("DELETE FROM bons_commande_lignes WHERE bon_commande_id IN (SELECT id FROM bons_commande WHERE center_id = ? "
                + "AND created_by = 'seed')", CENTRE);
        jdbc.update("DELETE FROM bons_commande WHERE center_id = ? AND created_by = 'seed'", CENTRE);
        jdbc.update("DELETE FROM stock_movements WHERE center_id = ? AND article_id IN (" + articlesSeed + ")", CENTRE, CENTRE);
        jdbc.update("DELETE FROM lots WHERE center_id = ? AND article_id IN (" + articlesSeed + ")", CENTRE, CENTRE);
        jdbc.update("DELETE FROM articles WHERE center_id = ? AND code IN ('" + String.join("','", CODES) + "')", CENTRE);
        jdbc.update("DELETE FROM fournisseurs WHERE center_id = ? AND code IN ('FRES', 'BAX', 'BRAUN')", CENTRE);
        jdbc.update("DELETE FROM emplacements WHERE center_id = ? AND code IN ('PHARM', 'RESA')", CENTRE);
        jdbc.update("DELETE FROM app_settings WHERE center_id = ?", CENTRE);
        for (Map<String, Object> c : compteurs) {
            jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, ?)", CENTRE, c.get("cle"),
                    c.get("prefixe"), c.get("dernier_compteur"));
        }
    }

    @Autowired
    private org.springframework.context.ApplicationContext contexte;

    @Test
    void le_jeu_de_demonstration_n_est_pas_active_quand_app_stock_demo_data_est_faux() {
        assertThat(contexte.containsBean("stockDemoDataRunner")).isFalse();
    }

    @Test
    void le_jeu_de_demonstration_est_ecrit_par_les_cas_d_usage_du_stock_et_n_est_ecrit_qu_une_fois() throws Exception {
        seeder().run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fournisseurs WHERE center_id = ?", Integer.class, CENTRE)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emplacements WHERE center_id = ? AND code IN ('PHARM', 'RESA')",
                Integer.class, CENTRE)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM articles WHERE center_id = ? AND code IN ('" + String.join("','", CODES)
                + "')", Integer.class, CENTRE)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bons_reception WHERE center_id = ? AND created_by = 'seed' "
                + "AND statut = 'VALIDE'", Integer.class, CENTRE)).isEqualTo(2);
        // Dialyseur : 100 à 350 puis 50 à 380 => PMP 360 ; la sortie FEFO de 10 prend le lot qui périme le plus tôt (LOT-DIA-B).
        assertThat(valeur("SELECT stock_quantity FROM articles WHERE center_id = ? AND code = 'DIA-FX60'", CENTRE))
                .isEqualByComparingTo("140");
        assertThat(valeur("SELECT pmp_courant FROM articles WHERE center_id = ? AND code = 'DIA-FX60'", CENTRE))
                .isEqualByComparingTo("360");
        assertThat(valeur("SELECT quantite_restante FROM lots WHERE center_id = ? AND numero_lot = 'LOT-DIA-B'", CENTRE))
                .isEqualByComparingTo("40");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bons_sortie WHERE center_id = ? AND created_by = 'seed'", Integer.class,
                CENTRE)).isEqualTo(1);

        seeder().run(new DefaultApplicationArguments());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fournisseurs WHERE center_id = ?", Integer.class, CENTRE))
                .as("idempotent").isEqualTo(3);
    }
}
