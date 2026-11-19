package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.inventaire.ConsulterLesLignesDInventaire;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.EtatDuStockPagineJdbcTest.AUTRE;
import static com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.EtatDuStockPagineJdbcTest.CENTRE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Feuille de comptage paginée sur H2 : ordre de la feuille, états, recherche, tri des écarts (non comptées en dernier). */
class LignesDInventairePagineesJdbcTest {

    private JdbcTemplate jdbc;
    private ConsulterLesLignesDInventaire lignes;
    private UUID inventaire;
    private int position;

    private UUID inventaire(UUID centre, String reference) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_at) VALUES (?, ?, ?, ?, "
                + "'EN_COURS', ?)", id, centre, reference, LocalDate.of(2026, 11, 1), OffsetDateTime.now());
        return id;
    }

    private void ligne(String code, String libelle, String lot, String theorique, String comptee, String pmp) {
        jdbc.update("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, article_code, "
                        + "article_libelle, unite, numero_lot, quantite_theorique, quantite_comptee, pmp, ajoutee) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'piece', ?, ?, ?, ?, FALSE)", UUID.randomUUID(), inventaire, CENTRE, position++,
                UUID.randomUUID(), code, libelle, lot, new BigDecimal(theorique), comptee == null ? null : new BigDecimal(comptee),
                new BigDecimal(pmp));
    }

    @BeforeEach
    void preparer() {
        jdbc = EtatDuStockPagineJdbcTest.baseDuStock();
        lignes = new ConsulterLesLignesDInventaire(new LignesDInventairePagineesJdbc(jdbc));
        inventaire = inventaire(CENTRE, "INV-00001");
        ligne("C", "Compresse", "LOT-1", "5.000", "5.000", "2");
        ligne("A", "Aiguille", null, "3.000", null, "1");
        ligne("B", "Bande", "LOT-2", "4.000", "1.000", "10");
        ligne("D", "Dialyseur", "X-7", "2.000", "6.000", "1");
    }

    private static List<String> codes(PageDeResultats<LigneDInventaire> page) {
        return page.items().stream().map(LigneDInventaire::articleCode).toList();
    }

    @Test
    void ordre_de_la_feuille_par_defaut_quantites_sans_zeros_inutiles() {
        var page = lignes.lignes(new CentreId(CENTRE), inventaire, 0, 3, null, null, null);
        assertEquals(List.of("C", "A", "B"), codes(page));
        assertEquals(4, page.total());
        assertEquals(new BigDecimal("5"), page.items().get(0).quantiteTheorique());
    }

    @Test
    void etats_de_comptage_et_recherche_code_libelle_ou_lot() {
        assertEquals(List.of("A"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, null, "A_COMPTER", null)));
        assertEquals(List.of("C", "B", "D"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, null, "COMPTEES", null)));
        assertEquals(List.of("B", "D"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, null, "ECARTS", null)));
        assertEquals(List.of("C", "A", "B", "D"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, null, "TOUTES", null)));
        assertEquals(List.of("D"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, null, null, "x-7")));
        assertEquals(List.of("A"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, null, null, "aigu")));
    }

    @Test
    void tri_des_ecarts_et_valeurs_d_ecart_lignes_non_comptees_en_dernier() {
        assertEquals(List.of("B", "C", "D", "A"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, "ecart", null, null)));
        assertEquals(List.of("D", "C", "B", "A"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, "ecart,desc", null,
                null)));
        assertEquals(List.of("B", "C", "D", "A"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, "valeurEcart", null,
                null)));
        assertEquals(List.of("D", "C", "B", "A"), codes(lignes.lignes(new CentreId(CENTRE), inventaire, null, null, "article,desc", null,
                null)));
    }

    @Test
    void inventaire_d_un_autre_centre_inconnu() {
        UUID ailleurs = inventaire(AUTRE, "INV-00001");
        assertEquals("INVENTORY_NOT_FOUND", assertThrows(RefusDuReferentiel.class,
                () -> lignes.lignes(new CentreId(CENTRE), ailleurs, null, null, null, null, null)).code());
    }
}
