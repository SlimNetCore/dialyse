package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferentielsLectureServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));

    private ReferentielsLectureService nouveauService() {
        return new ReferentielsLectureService(new FakeCatalogue(), new FakeGenerateurs());
    }

    @Test
    void lister_articles_actifs_tries_par_libelle() {
        ReferentielsLectureService service = nouveauService();

        PagedResult<ReferentielsLectureUseCases.ArticleLu> page = service.listerArticles(CENTRE, 0, 20);

        assertEquals(2, page.total());
        assertEquals("Bandelette", page.items().get(0).libelle());
        assertEquals("Gant", page.items().get(1).libelle());
    }

    @Test
    void lister_articles_pagine() {
        ReferentielsLectureService service = nouveauService();

        PagedResult<ReferentielsLectureUseCases.ArticleLu> premiere =
                service.listerArticles(CENTRE, 0, 1);

        assertEquals(2, premiere.total());
        assertEquals(1, premiere.items().size());
        assertEquals(0, premiere.page());
    }

    @Test
    void lister_generateurs_du_centre() {
        ReferentielsLectureService service = nouveauService();

        List<ReferentielsLectureUseCases.GenerateurLu> generateurs = service.listerGenerateurs(CENTRE);

        assertEquals(1, generateurs.size());
        assertEquals("G-01", generateurs.get(0).code());
        assertTrue(generateurs.get(0).nom().contains("G-01"));
    }

    private static class FakeCatalogue implements CatalogueDArticles {
        @Override
        public Optional<ArticleDuCatalogue> article(UUID centre, UUID article) {
            return Optional.empty();
        }

        @Override
        public List<ArticleDuCatalogue> articles(UUID centre) {
            return List.of(
                    new ArticleDuCatalogue(UUID.randomUUID(), centre, "GANT", "Gant", "boîte",
                            true, null, null, null, false, null, null),
                    new ArticleDuCatalogue(UUID.randomUUID(), centre, "BAND", "Bandelette", "pièce",
                            true, null, null, null, false, null, null),
                    new ArticleDuCatalogue(UUID.randomUUID(), centre, "VIEUX", "Vieux", "pièce",
                            false, null, null, null, false, null, null));
        }

        @Override
        public List<ArticleDuCatalogue> articlesDeTraitement(UUID centre, String typeTraitementAnemie) {
            return List.of();
        }

        @Override
        public BigDecimal quantitePourDose(UUID centre, UUID article, BigDecimal dose, String uniteDose) {
            return BigDecimal.ONE;
        }
    }

    private static class FakeGenerateurs implements GenerateursDuCentre {
        @Override
        public List<Generateur> duCentre(UUID centreId) {
            return List.of(new Generateur(UUID.randomUUID(), "G-01", null, UUID.randomUUID(),
                    "EN_SERVICE", centreId));
        }
    }
}
