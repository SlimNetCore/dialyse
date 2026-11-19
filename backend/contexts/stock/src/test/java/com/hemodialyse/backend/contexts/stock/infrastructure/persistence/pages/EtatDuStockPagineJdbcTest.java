package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.etat.ConsulterLEtatDuStock;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** État du stock paginé sur H2 (mode PostgreSQL, schéma du contexte) : COUNT + LIMIT/OFFSET, tri, filtres, isolation. */
class EtatDuStockPagineJdbcTest {

    static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    static JdbcTemplate baseDuStock() {
        // une seule connexion : H2 évalue les CHECK dans la session qui les a créées
        SingleConnectionDataSource source = new SingleConnectionDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/h2/stock-schema.sql")).execute(source);
        return new JdbcTemplate(source);
    }

    static UUID article(JdbcTemplate jdbc, UUID centre, String code, String libelle, String qte, String pmp, boolean actif) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot, "
                + "active) VALUES (?, ?, ?, ?, 'piece', ?, 0, ?, FALSE, ?)", id, centre, code, libelle, new BigDecimal(qte),
                pmp == null ? null : new BigDecimal(pmp), actif);
        return id;
    }

    private JdbcTemplate jdbc;
    private ConsulterLEtatDuStock etat;

    @BeforeEach
    void preparer() {
        jdbc = baseDuStock();
        etat = new ConsulterLEtatDuStock(new EtatDuStockPagineJdbc(jdbc));
        article(jdbc, CENTRE, "C-03", "Compresse", "10.00", "2.50", true);
        article(jdbc, CENTRE, "A-01", "aiguille", "3.00", "4.00", true);
        article(jdbc, CENTRE, "B-02", "Bande 100%", "0.00", null, true);
        article(jdbc, CENTRE, "D-04", "Désactivé", "99.00", "1.00", false);
        article(jdbc, AUTRE, "A-00", "Autre centre", "1.00", "1.00", true);
    }

    private List<String> codes(PageDeResultats<ArticleValorise> page) {
        return page.items().stream().map(ArticleValorise::code).toList();
    }

    @Test
    void articles_actifs_du_centre_par_code_avec_valeur_quantite_fois_pmp() {
        var page = etat.page(new CentreId(CENTRE), null, null, null, null, null, null, null, null);
        assertEquals(List.of("A-01", "B-02", "C-03"), codes(page));
        assertEquals(3, page.total());
        assertEquals(new BigDecimal("25.0000"), page.items().get(2).valeur());
        assertEquals(0, page.items().get(1).pmpCourant().signum());
    }

    @Test
    void page_limitee_total_filtre_et_page_au_dela_de_la_fin() {
        var page = etat.page(new CentreId(CENTRE), 1, 2, null, null, null, null, null, null);
        assertEquals(List.of("C-03"), codes(page));
        assertEquals(3, page.total());
        var vide = etat.page(new CentreId(CENTRE), 5, 2, null, null, null, null, null, null);
        assertEquals(List.of(), codes(vide));
        assertEquals(3, vide.total());
    }

    @Test
    void tri_par_valeur_decroissante_et_libelle_sans_casse() {
        assertEquals(List.of("C-03", "A-01", "B-02"), codes(etat.page(new CentreId(CENTRE), 0, 20, "valeur,desc", null, null, null,
                null, null)));
        assertEquals(List.of("A-01", "B-02", "C-03"), codes(etat.page(new CentreId(CENTRE), 0, 20, "libelle", null, null, null, null,
                null)));
    }

    @Test
    void filtres_texte_echappes_et_numeriques() {
        assertEquals(List.of("B-02"), codes(etat.page(new CentreId(CENTRE), 0, 20, null, null, "100%", null, null, null)));
        assertEquals(List.of(), codes(etat.page(new CentreId(CENTRE), 0, 20, null, null, "_", null, null, null)));
        assertEquals(List.of("A-01", "C-03"), codes(etat.page(new CentreId(CENTRE), 0, 20, null, "-0", null, ">0", null, null)));
        assertEquals(List.of("A-01"), codes(etat.page(new CentreId(CENTRE), 0, 20, null, null, null, null, "=4", "<25")));
        assertEquals(1, etat.page(new CentreId(CENTRE), 0, 20, null, null, null, null, null, ">= 25,0").total());
    }
}
