package com.hemodialyse.backend.contexts.stock.application.consultation.etat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage.Comparateur;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsulterLEtatDuStockTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

    /** Fake en mémoire du port : filtre, trie et découpe comme le ferait le SQL. */
    static final class EtatEnMemoire implements PagesDeLEtatDuStock {
        final List<ArticleValorise> articles = new ArrayList<>();
        Filtres derniersFiltres;
        DemandeDePage derniereDemande;

        @Override
        public PageDeResultats<ArticleValorise> page(CentreId centre, Filtres filtres, DemandeDePage demande) {
            derniersFiltres = filtres;
            derniereDemande = demande;
            Function<ArticleValorise, Comparable> cle = switch (demande.champ()) {
                case "libelle" -> a -> a.libelle().toLowerCase();
                case "quantite" -> ArticleValorise::quantite;
                case "pmp" -> ArticleValorise::pmpCourant;
                case "valeur" -> ArticleValorise::valeur;
                default -> ArticleValorise::code;
            };
            @SuppressWarnings("unchecked")
            Comparator<ArticleValorise> ordre = Comparator.comparing(a -> (Comparable<Object>) cle.apply(a));
            List<ArticleValorise> retenus = articles.stream()
                    .filter(a -> filtres.code().map(t -> a.code().toLowerCase().contains(t)).orElse(true))
                    .filter(a -> filtres.libelle().map(t -> a.libelle().toLowerCase().contains(t)).orElse(true))
                    .filter(a -> filtres.quantite().map(f -> f.accepte(a.quantite())).orElse(true))
                    .filter(a -> filtres.pmp().map(f -> f.accepte(a.pmpCourant())).orElse(true))
                    .filter(a -> filtres.valeur().map(f -> f.accepte(a.valeur())).orElse(true))
                    .sorted(demande.descendant() ? ordre.reversed() : ordre).toList();
            return PageDeResultats.decouper(retenus, demande);
        }
    }

    private static ArticleValorise article(String code, String libelle, String qte, String pmp) {
        BigDecimal q = new BigDecimal(qte);
        BigDecimal p = new BigDecimal(pmp);
        return new ArticleValorise(UUID.randomUUID(), code, libelle, "piece", q, p, q.multiply(p));
    }

    private final EtatEnMemoire fake = new EtatEnMemoire();
    private final ConsulterLEtatDuStock etat = new ConsulterLEtatDuStock(fake);

    @Test
    void par_defaut_page_0_de_20_triee_par_code_croissant() {
        fake.articles.addAll(List.of(article("B", "Beta", "1", "2"), article("A", "Alpha", "3", "1")));
        PageDeResultats<ArticleValorise> page = etat.page(CENTRE, null, null, null, null, null, null, null, null);
        assertEquals(new DemandeDePage(0, 20, "code", false), fake.derniereDemande);
        assertEquals(List.of("A", "B"), page.items().stream().map(ArticleValorise::code).toList());
        assertEquals(2, page.total());
    }

    @Test
    void filtres_texte_et_numeriques_lus_puis_appliques_par_le_port() {
        fake.articles.addAll(List.of(article("AIG-1", "Aiguille", "10", "2"), article("AIG-2", "Aiguille fine", "1", "2"),
                article("SER", "Seringue", "50", "1")));
        PageDeResultats<ArticleValorise> page = etat.page(CENTRE, 0, 10, "valeur,desc", " aig ", null, ">=5", null, "<=20");
        assertEquals("aig", fake.derniersFiltres.code().orElseThrow());
        assertEquals(Comparateur.SUPERIEUR_OU_EGAL, fake.derniersFiltres.quantite().orElseThrow().comparateur());
        assertEquals(List.of("AIG-1"), page.items().stream().map(ArticleValorise::code).toList());
        assertTrue(fake.derniereDemande.descendant());
    }

    @Test
    void codes_400_stables() {
        assertEquals(PaginationInvalide.PAGE_INVALIDE,
                assertThrows(PaginationInvalide.class, () -> etat.page(CENTRE, -1, null, null, null, null, null, null, null)).code());
        assertEquals(PaginationInvalide.TAILLE_INVALIDE,
                assertThrows(PaginationInvalide.class, () -> etat.page(CENTRE, 0, 101, null, null, null, null, null, null)).code());
        assertEquals(PaginationInvalide.TRI_INVALIDE,
                assertThrows(PaginationInvalide.class, () -> etat.page(CENTRE, 0, 20, "unite", null, null, null, null, null)).code());
        assertEquals(PaginationInvalide.FILTRE_INVALIDE,
                assertThrows(PaginationInvalide.class, () -> etat.page(CENTRE, 0, 20, null, null, null, "beaucoup", null, null)).code());
    }
}
