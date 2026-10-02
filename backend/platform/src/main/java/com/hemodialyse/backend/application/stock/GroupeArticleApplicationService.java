package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import com.hemodialyse.backend.domain.stock.port.GroupeArticleRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administration des groupes d'articles d'un centre. Les groupes ne contiennent que des articles du centre ;
 * le nom est unique par centre (insensible à la casse et aux accents).
 */
@Service
public class GroupeArticleApplicationService {

    private final GroupeArticleRepositoryPort groupes;
    private final ArticleRepositoryPort articles;

    public GroupeArticleApplicationService(GroupeArticleRepositoryPort groupes, ArticleRepositoryPort articles) {
        this.groupes = groupes;
        this.articles = articles;
    }

    public PagedResult<GroupeArticle> lister(UUID centerId, int page, int size) {
        return groupes.findPaged(centerId, page, size);
    }

    public GroupeArticle obtenir(UUID centerId, UUID id) {
        return groupes.findById(centerId, id).orElseThrow(
                () -> new BusinessException("GROUPE_ARTICLE_INTROUVABLE", "Groupe d'articles introuvable"));
    }

    public GroupeArticle creer(UUID centerId, String nom, String description, Collection<UUID> articleIds) {
        GroupeArticle groupe = GroupeArticle.creer(centerId, nom, description, articleIds);
        verifierNomLibre(centerId, groupe.cle(), null);
        verifierArticlesDuCentre(centerId, groupe.articleIds());
        return groupes.save(groupe);
    }

    public GroupeArticle modifier(UUID centerId, UUID id, String nom, String description, Collection<UUID> articleIds) {
        GroupeArticle modifie = obtenir(centerId, id).modifier(nom, description, articleIds);
        verifierNomLibre(centerId, modifie.cle(), id);
        verifierArticlesDuCentre(centerId, modifie.articleIds());
        return groupes.save(modifie);
    }

    public void supprimer(UUID centerId, UUID id) {
        obtenir(centerId, id);
        groupes.delete(centerId, id);
    }

    private void verifierNomLibre(UUID centerId, String cle, UUID excludeId) {
        if (groupes.existsByCle(centerId, cle, excludeId)) {
            throw new BusinessException("GROUPE_ARTICLE_NOM_EXISTANT", "Un groupe porte déjà ce nom dans ce centre");
        }
    }

    /**
     * Isolation multi-centre : un identifiant d'article d'un autre centre est refusé.
     */
    private void verifierArticlesDuCentre(UUID centerId, Set<UUID> articleIds) {
        Set<UUID> duCentre = articles.findAllByCenter(CenterId.of(centerId)).stream()
                .map(Article::getId).collect(Collectors.toSet());
        if (!duCentre.containsAll(articleIds)) {
            throw new BusinessException("GROUPE_ARTICLE_ARTICLE_INCONNU",
                    "Le groupe contient un article qui n'appartient pas à ce centre");
        }
    }
}
