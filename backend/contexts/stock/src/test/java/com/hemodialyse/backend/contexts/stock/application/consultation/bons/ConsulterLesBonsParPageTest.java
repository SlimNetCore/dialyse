package com.hemodialyse.backend.contexts.stock.application.consultation.bons;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsulterLesBonsParPageTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

    /** Faux port en mémoire : retient la dernière demande reçue et renvoie une page vide. */
    private static final class PagesEnMemoire implements PagesDesBons {
        Object filtre;
        DemandeDePage demande;
        CentreId centre;

        private <T> PageDeResultats<T> retenir(CentreId c, Object f, DemandeDePage d) {
            centre = c;
            filtre = f;
            demande = d;
            return PageDeResultats.decouper(List.of(), d);
        }

        @Override
        public PageDeResultats<BonDeCommande> bonsDeCommande(CentreId c, FiltreDesBonsDeCommande f, DemandeDePage d) {
            return retenir(c, f, d);
        }

        @Override
        public PageDeResultats<BonDeReception> bonsDeReception(CentreId c, FiltreDesBonsDeReception f, DemandeDePage d) {
            return retenir(c, f, d);
        }

        @Override
        public PageDeResultats<BonDeSortie> bonsDeSortie(CentreId c, FiltreDesBonsDeSortie f, DemandeDePage d) {
            return retenir(c, f, d);
        }
    }

    private final PagesEnMemoire pages = new PagesEnMemoire();
    private final ConsulterLesBonsParPage consulter = new ConsulterLesBonsParPage(pages);

    private static String code(Runnable appel) {
        return assertThrows(PaginationInvalide.class, appel::run).code();
    }

    @Test
    void defaut_creation_la_plus_recente_d_abord_et_filtres_vides_ignores() {
        var page = consulter.bonsDeCommande(CENTRE, new ParametresDePage(null, null, null), "  ", null);
        assertEquals(new DemandeDePage(0, 20, "createdAt", true), pages.demande);
        assertEquals(new PagesDesBons.FiltreDesBonsDeCommande(null, Set.of()), pages.filtre);
        assertEquals(CENTRE, pages.centre);
        assertEquals(0, page.total());
    }

    @Test
    void bons_de_commande_tri_et_filtres_lus() {
        consulter.bonsDeCommande(CENTRE, new ParametresDePage(2, 50, "lignes,desc"), " BC-1 ", "VALIDE,RECU");
        assertEquals(new DemandeDePage(2, 50, "lignes", true), pages.demande);
        assertEquals(new PagesDesBons.FiltreDesBonsDeCommande("bc-1", Set.of(StatutDuBon.VALIDE, StatutDuBon.RECU)),
                pages.filtre);
    }

    @Test
    void bons_de_reception_statuts_reels_seulement() {
        consulter.bonsDeReception(CENTRE, new ParametresDePage(0, 10, "dateReception"), null, "BROUILLON");
        assertEquals(new DemandeDePage(0, 10, "dateReception", false), pages.demande);
        assertEquals(new PagesDesBons.FiltreDesBonsDeReception(null, Set.of(BonDeReception.Statut.BROUILLON)), pages.filtre);
        assertEquals("FILTRE_INVALIDE", code(() -> consulter.bonsDeReception(CENTRE, new ParametresDePage(null, null, null),
                null, "RECU")));
        assertEquals("FILTRE_INVALIDE", code(() -> consulter.bonsDeCommande(CENTRE, new ParametresDePage(null, null, null),
                null, "ANNULE")));
    }

    @Test
    void bons_de_sortie_sans_statut_ni_tri_par_statut() {
        consulter.bonsDeSortie(CENTRE, new ParametresDePage(1, 100, "dateSortie,asc"), "bs");
        assertEquals(new DemandeDePage(1, 100, "dateSortie", false), pages.demande);
        assertEquals(new PagesDesBons.FiltreDesBonsDeSortie("bs"), pages.filtre);
        assertEquals("TRI_INVALIDE", code(() -> consulter.bonsDeSortie(CENTRE, new ParametresDePage(0, 20, "statut"), null)));
    }

    @Test
    void refus_avec_codes_stables_avant_toute_lecture() {
        pages.demande = null;
        assertEquals("PAGE_INVALIDE", code(() -> consulter.bonsDeCommande(CENTRE, new ParametresDePage(-1, 20, null), null, null)));
        assertEquals("TAILLE_INVALIDE", code(() -> consulter.bonsDeReception(CENTRE, new ParametresDePage(0, 101, null), null,
                null)));
        assertEquals("TRI_INVALIDE", code(() -> consulter.bonsDeCommande(CENTRE, new ParametresDePage(0, 20, "id"), null, null)));
        assertEquals("TRI_INVALIDE", code(() -> consulter.bonsDeCommande(CENTRE, new ParametresDePage(0, 20, "reference,up"),
                null, null)));
        assertNull(pages.demande);
    }
}
