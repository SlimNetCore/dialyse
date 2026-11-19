package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.ConsulterLesSortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances.LigneDetaillee;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances.ResumeDePiece;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.EtatDuStockPagineJdbcTest.AUTRE;
import static com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.EtatDuStockPagineJdbcTest.CENTRE;
import static com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.EtatDuStockPagineJdbcTest.article;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Sorties pour séances paginées sur H2 : pièces (nombre de lignes, tri, filtres, isolation) et lignes d'une pièce. */
class SortiesPourSeancesPagineesJdbcTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    private JdbcTemplate jdbc;
    private ConsulterLesSortiesPourSeances sorties;
    private UUID aiguille;
    private UUID bande;

    private UUID piece(UUID centre, String reference, String statut, int heures) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO sorties_pour_seances (id, center_id, reference, jour, statut, enregistree_le, enregistree_par) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'u')", id, centre, reference, LocalDate.of(2026, 11, 2), statut, T0.plusHours(heures));
        return id;
    }

    private void ligne(UUID piece, UUID article, String sortie, String ecart) {
        jdbc.update("INSERT INTO sorties_pour_seances_lignes (id, sortie_id, article_id, quantite_sortie, valeur_unitaire, "
                        + "quantite_consommee, ecart) VALUES (?, ?, ?, ?, 1, ?, ?)", UUID.randomUUID(), piece, article, new BigDecimal(sortie),
                ecart == null ? null : new BigDecimal(sortie).subtract(new BigDecimal(ecart)), ecart == null ? null : new BigDecimal(ecart));
    }

    @BeforeEach
    void preparer() {
        jdbc = EtatDuStockPagineJdbcTest.baseDuStock();
        sorties = new ConsulterLesSortiesPourSeances(new SortiesPourSeancesPagineesJdbc(jdbc));
        aiguille = article(jdbc, CENTRE, "AIG", "Aiguille", "10", "1", true);
        bande = article(jdbc, CENTRE, "BDE", "Bande", "10", "1", true);
    }

    private static List<String> references(PageDeResultats<ResumeDePiece> page) {
        return page.items().stream().map(ResumeDePiece::reference).toList();
    }

    @Test
    void pieces_du_centre_sans_lignes_avec_leur_nombre_les_plus_recentes_d_abord() {
        UUID p1 = piece(CENTRE, "SPS-00001", "RAPPROCHEE", 0);
        ligne(p1, aiguille, "2", "0");
        ligne(p1, bande, "1", "1");
        piece(CENTRE, "SPS-00002", "ENREGISTREE", 1);
        piece(AUTRE, "SPS-00009", "ENREGISTREE", 2);
        var page = sorties.pieces(new CentreId(CENTRE), null, null, null, null, null);
        assertEquals(List.of("SPS-00002", "SPS-00001"), references(page));
        assertEquals(List.of(0, 2), page.items().stream().map(ResumeDePiece::nombreDeLignes).toList());
        assertEquals(2, page.total());
    }

    @Test
    void pieces_filtrees_par_statut_et_reference_triees_par_nombre_de_lignes() {
        UUID p1 = piece(CENTRE, "SPS-00001", "RAPPROCHEE", 0);
        ligne(p1, aiguille, "2", null);
        piece(CENTRE, "SPS-00002", "ENREGISTREE", 1);
        piece(CENTRE, "SPS-00010", "ENREGISTREE", 2);
        assertEquals(List.of("SPS-00010", "SPS-00002"), references(sorties.pieces(new CentreId(CENTRE), 0, 20, null, null,
                "ENREGISTREE")));
        assertEquals(List.of("SPS-00010"), references(sorties.pieces(new CentreId(CENTRE), 0, 20, null, "0010", null)));
        assertEquals(List.of("SPS-00001"), references(sorties.pieces(new CentreId(CENTRE), 0, 1, "lignes,desc", null, null)));
    }

    @Test
    void lignes_d_une_piece_avec_article_lisible_filtrees_et_triees() {
        UUID p1 = piece(CENTRE, "SPS-00001", "ENREGISTREE", 0);
        ligne(p1, bande, "1", null);
        ligne(p1, aiguille, "2", "1");
        var page = sorties.lignes(new CentreId(CENTRE), p1, null, null, null, null);
        assertEquals(List.of("Aiguille", "Bande"), page.items().stream().map(LigneDetaillee::articleLibelle).toList());
        assertEquals("AIG", page.items().get(0).articleCode());
        var parEcart = sorties.lignes(new CentreId(CENTRE), p1, 0, 20, "ecart,desc", null);
        assertEquals("AIG", parEcart.items().get(0).articleCode());
        assertNull(parEcart.items().get(1).ligne().ecart());
        var filtrees = sorties.lignes(new CentreId(CENTRE), p1, 0, 20, null, "bd");
        assertEquals(1, filtrees.total());
        assertEquals("Bande", filtrees.items().get(0).articleLibelle());
    }

    @Test
    void lignes_d_une_piece_d_un_autre_centre_refusees() {
        UUID ailleurs = piece(AUTRE, "SPS-00001", "ENREGISTREE", 0);
        assertEquals("PIECE_INTROUVABLE", assertThrows(RefusDuReferentiel.class,
                () -> sorties.lignes(new CentreId(CENTRE), ailleurs, null, null, null, null)).code());
    }
}
