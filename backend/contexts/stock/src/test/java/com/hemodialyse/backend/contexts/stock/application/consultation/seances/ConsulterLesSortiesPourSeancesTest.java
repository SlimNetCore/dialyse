package com.hemodialyse.backend.contexts.stock.application.consultation.seances;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsulterLesSortiesPourSeancesTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

    /** Fake en mémoire : retient les demandes reçues ; seules les pièces enregistrées « existent ». */
    static final class SortiesEnMemoire implements PagesDesSortiesPourSeances {
        final Set<UUID> pieces = new HashSet<>();
        FiltresDesPieces filtres;
        Optional<String> article;
        DemandeDePage demande;

        @Override
        public PageDeResultats<ResumeDePiece> pieces(CentreId centre, FiltresDesPieces filtres, DemandeDePage demande) {
            this.filtres = filtres;
            this.demande = demande;
            return new PageDeResultats<>(List.of(), 0, demande.page(), demande.taille());
        }

        @Override
        public boolean existe(CentreId centre, UUID piece) {
            return pieces.contains(piece);
        }

        @Override
        public PageDeResultats<LigneDetaillee> lignes(CentreId centre, UUID piece, Optional<String> article, DemandeDePage demande) {
            this.article = article;
            this.demande = demande;
            return new PageDeResultats<>(List.of(), 0, demande.page(), demande.taille());
        }
    }

    private final SortiesEnMemoire fake = new SortiesEnMemoire();
    private final ConsulterLesSortiesPourSeances sorties = new ConsulterLesSortiesPourSeances(fake);

    @Test
    void pieces_par_defaut_enregistrees_le_plus_recemment_d_abord() {
        sorties.pieces(CENTRE, null, null, null, null, null);
        assertEquals(new DemandeDePage(0, 20, "enregistreeLe", true), fake.demande);
        assertEquals(Set.of(), fake.filtres.statuts());
    }

    @Test
    void pieces_filtrees_par_reference_et_statuts() {
        sorties.pieces(CENTRE, 2, 50, "lignes,asc", " SPS-0 ", "ENREGISTREE,RAPPROCHEE");
        assertEquals("sps-0", fake.filtres.reference().orElseThrow());
        assertEquals(Set.of(SortiePourSeances.Statut.ENREGISTREE, SortiePourSeances.Statut.RAPPROCHEE), fake.filtres.statuts());
        assertEquals(new DemandeDePage(2, 50, "lignes", false), fake.demande);
    }

    @Test
    void statut_inconnu_ou_tri_hors_liste_refuses() {
        assertEquals(PaginationInvalide.FILTRE_INVALIDE, assertThrows(PaginationInvalide.class,
                () -> sorties.pieces(CENTRE, 0, 20, null, null, "VALIDE")).code());
        assertEquals(PaginationInvalide.TRI_INVALIDE, assertThrows(PaginationInvalide.class,
                () -> sorties.pieces(CENTRE, 0, 20, "salle", null, null)).code());
    }

    @Test
    void lignes_d_une_piece_du_centre_triees_par_article_par_defaut() {
        UUID piece = UUID.randomUUID();
        fake.pieces.add(piece);
        sorties.lignes(CENTRE, piece, null, null, null, " Aig ");
        assertEquals(new DemandeDePage(0, 20, "article", false), fake.demande);
        assertEquals("aig", fake.article.orElseThrow());
    }

    @Test
    void lignes_d_une_piece_inconnue_du_centre_refusees_comme_sa_consultation() {
        RefusDuReferentiel refus = assertThrows(RefusDuReferentiel.class,
                () -> sorties.lignes(CENTRE, UUID.randomUUID(), null, null, null, null));
        assertEquals("PIECE_INTROUVABLE", refus.code());
        assertNull(fake.demande);
    }
}
