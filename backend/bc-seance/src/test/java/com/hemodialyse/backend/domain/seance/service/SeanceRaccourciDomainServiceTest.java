package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeanceRaccourciDomainServiceTest {

    private final CenterId centre = CenterId.of(UUID.randomUUID());
    private final Map<UUID, List<UUID>> stored = new HashMap<>();
    private final ArticleRepositoryPort articles = mock(ArticleRepositoryPort.class);
    private final SeanceRaccourciRepositoryPort repo = new SeanceRaccourciRepositoryPort() {
        @Override
        public List<UUID> findArticleIds(CenterId centerId) {
            return stored.getOrDefault(centerId.value(), List.of());
        }

        @Override
        public void replace(CenterId centerId, List<UUID> articleIds) {
            stored.put(centerId.value(), new ArrayList<>(articleIds));
        }
    };
    private final SeanceRaccourciUseCase service = new SeanceRaccourciDomainService(repo, articles);

    private UUID activeArticle() {
        UUID id = UUID.randomUUID();
        Article article = new Article();
        article.setId(id);
        article.setCode("A-" + id.toString().substring(0, 4));
        article.setActive(true);
        when(articles.findById(id, centre)).thenReturn(Optional.of(article));
        return id;
    }

    @Test
    void a_center_without_shortcuts_gets_an_empty_list() {
        assertEquals(List.of(), service.get(centre));
    }

    @Test
    void replace_stores_the_shortcuts_in_the_chosen_order_and_replaces_the_previous_ones() {
        UUID a = activeArticle();
        UUID b = activeArticle();
        UUID c = activeArticle();

        service.replace(centre, List.of(a, b));
        assertEquals(List.of(a, b), service.get(centre));

        service.replace(centre, List.of(c, a));
        assertEquals(List.of(c, a), service.get(centre));
    }

    @Test
    void shortcuts_are_isolated_by_center() {
        UUID a = activeArticle();
        service.replace(centre, List.of(a));

        assertEquals(List.of(), service.get(CenterId.of(UUID.randomUUID())));
    }

    @Test
    void an_empty_list_clears_the_shortcuts() {
        service.replace(centre, List.of(activeArticle()));

        service.replace(centre, List.of());

        assertEquals(List.of(), service.get(centre));
    }

    @Test
    void refuses_a_duplicate_an_unknown_article_an_inactive_article_and_too_many() {
        UUID a = activeArticle();
        assertThrows(IllegalArgumentException.class, () -> service.replace(centre, List.of(a, a)));

        UUID unknown = UUID.randomUUID();
        when(articles.findById(unknown, centre)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.replace(centre, List.of(unknown)));

        UUID inactive = UUID.randomUUID();
        Article article = new Article();
        article.setId(inactive);
        article.setCode("OLD");
        article.setActive(false);
        when(articles.findById(inactive, centre)).thenReturn(Optional.of(article));
        assertThrows(IllegalStateException.class, () -> service.replace(centre, List.of(inactive)));

        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i <= SeanceRaccourciUseCase.MAX; i++) {
            tooMany.add(activeArticle());
        }
        assertThrows(IllegalArgumentException.class, () -> service.replace(centre, tooMany));
        assertEquals(List.of(), service.get(centre), "un refus ne modifie pas la liste");
    }
}
