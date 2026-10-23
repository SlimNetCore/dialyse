package com.hemodialyse.backend.infrastructure.stock;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance.Ligne;
import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comportement historique des consommables relevés à la validation (centre sans sortie pour séances) : lots choisis en FEFO, répartis
 * sur plusieurs lots si besoin, refus avec la quantité manquante, un seul bon « SEANCE » créé.
 */
class ConsommationsDeSeanceLegacyTest {
    private static final UUID CENTRE = UUID.randomUUID();
    private final BonSortieUseCase bons = mock(BonSortieUseCase.class);
    private final LotRepositoryPort lots = mock(LotRepositoryPort.class);
    private final ArticleRepositoryPort articles = mock(ArticleRepositoryPort.class);
    private final ConsommationsDeSeanceLegacy legacy = new ConsommationsDeSeanceLegacy(bons, lots, articles);

    private Lot lot(UUID id, String quantite) {
        Lot lot = new Lot();
        lot.setId(id);
        lot.setQuantiteRestante(new BigDecimal(quantite));
        return lot;
    }

    private UUID article() {
        UUID id = UUID.randomUUID();
        Article article = mock(Article.class);
        when(article.getCode()).thenReturn("ART-03");
        when(article.getUnite()).thenReturn("piece");
        when(articles.findById(id, CenterId.of(CENTRE))).thenReturn(Optional.of(article));
        return id;
    }

    @Test
    void les_lots_sont_choisis_en_fefo_et_repartis_sur_plusieurs_lots() {
        UUID article = article();
        UUID lot1 = UUID.randomUUID();
        UUID lot2 = UUID.randomUUID();
        when(lots.findAvailableByArticleFefo(article, CenterId.of(CENTRE))).thenReturn(List.of(lot(lot1, "3"), lot(lot2, "5")));
        legacy.enregistrerALaValidation(CENTRE, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                List.of(new Ligne(article, new BigDecimal("5"))), "inf");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SortieRequestItem>> items = ArgumentCaptor.forClass(List.class);
        verify(bons).create(any(), any(), any(), org.mockito.ArgumentMatchers.eq("SEANCE"), any(), items.capture(), any());
        assertEquals(2, items.getValue().size());
        assertEquals(lot1, items.getValue().get(0).lotId());
        assertEquals(new BigDecimal("3"), items.getValue().get(0).quantite());
        assertEquals(lot2, items.getValue().get(1).lotId());
        assertEquals(new BigDecimal("2"), items.getValue().get(1).quantite());
    }

    @Test
    void un_stock_insuffisant_est_refuse_avec_la_quantite_manquante_sans_creer_de_bon() {
        UUID article = article();
        when(lots.findAvailableByArticleFefo(article, CenterId.of(CENTRE))).thenReturn(List.of());
        IllegalStateException refus = assertThrows(IllegalStateException.class, () -> legacy.enregistrerALaValidation(CENTRE,
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), List.of(new Ligne(article, new BigDecimal("3"))), "inf"));
        assertEquals("Stock insuffisant pour l'article ART-03 (manque: 3 piece)", refus.getMessage());
        verify(bons, never()).create(any(), any(), any(), any(), any(), anyList(), any());
    }
}