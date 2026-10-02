package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import com.hemodialyse.backend.domain.stock.port.GroupeArticleRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupeArticleApplicationServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();

    private final GroupeArticleRepositoryPort groupes = mock(GroupeArticleRepositoryPort.class);
    private final ArticleRepositoryPort articles = mock(ArticleRepositoryPort.class);
    private final GroupeArticleApplicationService service = new GroupeArticleApplicationService(groupes, articles);

    private UUID articleDuCentre() {
        UUID id = UUID.randomUUID();
        Article a = mock(Article.class);
        when(a.getId()).thenReturn(id);
        when(articles.findAllByCenter(any())).thenReturn(List.of(a));
        return id;
    }

    @Test
    void creer_should_persist_a_group_of_articles_of_the_center() {
        UUID article = articleDuCentre();
        when(groupes.existsByCle(eq(CENTRE), eq("kit cnas"), eq(null))).thenReturn(false);
        when(groupes.save(any())).thenAnswer(i -> i.getArgument(0));

        GroupeArticle g = service.creer(CENTRE, "Kit CNAS", null, List.of(article));

        assertEquals("Kit CNAS", g.nom());
        verify(groupes).save(any());
    }

    @Test
    void creer_should_refuse_a_duplicate_name_in_the_center() {
        UUID article = articleDuCentre();
        when(groupes.existsByCle(eq(CENTRE), eq("kit cnas"), eq(null))).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.creer(CENTRE, "KIT  cnas", null, List.of(article)));

        assertEquals("GROUPE_ARTICLE_NOM_EXISTANT", e.getCode());
        verify(groupes, never()).save(any());
    }

    @Test
    void creer_should_refuse_an_article_of_another_center() {
        articleDuCentre();

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.creer(CENTRE, "Kit", null, List.of(UUID.randomUUID())));

        assertEquals("GROUPE_ARTICLE_ARTICLE_INCONNU", e.getCode());
        verify(groupes, never()).save(any());
    }

    @Test
    void modifier_should_exclude_itself_from_the_name_check_and_keep_the_center() {
        UUID article = articleDuCentre();
        GroupeArticle existant = GroupeArticle.creer(CENTRE, "Kit", null, List.of(article));
        when(groupes.findById(CENTRE, existant.id())).thenReturn(Optional.of(existant));
        when(groupes.existsByCle(CENTRE, "kit cnas", existant.id())).thenReturn(false);
        when(groupes.save(any())).thenAnswer(i -> i.getArgument(0));

        GroupeArticle m = service.modifier(CENTRE, existant.id(), "Kit CNAS", "d", List.of(article));

        assertEquals(existant.id(), m.id());
        assertEquals(CENTRE, m.centerId());
        assertEquals("Kit CNAS", m.nom());
    }

    @Test
    void obtenir_and_supprimer_should_not_reach_a_group_of_another_center() {
        UUID id = UUID.randomUUID();
        when(groupes.findById(CENTRE, id)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.obtenir(CENTRE, id));
        assertThrows(BusinessException.class, () -> service.supprimer(CENTRE, id));
        verify(groupes, never()).delete(any(), any());
    }
}
