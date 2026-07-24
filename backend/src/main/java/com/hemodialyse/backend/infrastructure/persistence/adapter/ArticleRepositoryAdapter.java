package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.ArticleJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.ArticleJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ArticleRepositoryAdapter implements ArticleRepositoryPort {

    private final ArticleJpaRepository jpa;

    public ArticleRepositoryAdapter(ArticleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Article> findById(UUID articleId, CenterId centerId) {
        return jpa.findByIdAndCenterId(articleId, centerId.value()).map(this::toDomain);
    }

    @Override
    public Article save(Article article) {
        return toDomain(jpa.save(toJpa(article)));
    }

    @Override
    public List<Article> findAllByCenter(CenterId centerId) {
        return jpa.findByCenterIdOrderByCode(centerId.value()).stream().map(this::toDomain).toList();
    }

    private Article toDomain(ArticleJpaEntity e) {
        Article article = new Article();
        article.setId(e.getId());
        article.setCenterId(e.getCenterId());
        article.setCode(e.getCode());
        article.setLibelle(e.getLibelle());
        article.setUnite(e.getUnite());
        article.setStockQuantity(e.getStockQuantity());
        article.setSeuilAlerte(e.getSeuilAlerte());
        article.setPmpCourant(e.getPmpCourant());
        article.setGereParLot(e.isGereParLot());
        article.setActive(e.isActive());
        article.setCreatedAt(e.getCreatedAt());
        return article;
    }

    private ArticleJpaEntity toJpa(Article a) {
        ArticleJpaEntity entity = new ArticleJpaEntity();
        entity.setId(a.getId());
        entity.setCenterId(a.getCenterId());
        entity.setCode(a.getCode());
        entity.setLibelle(a.getLibelle());
        entity.setUnite(a.getUnite());
        entity.setStockQuantity(a.getStockQuantity());
        entity.setSeuilAlerte(a.getSeuilAlerte());
        entity.setPmpCourant(a.getPmpCourant());
        entity.setGereParLot(a.isGereParLot());
        entity.setActive(a.isActive());
        entity.setCreatedAt(a.getCreatedAt());
        return entity;
    }
}



