package com.hemodialyse.backend.domain.article.port;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Optional;
import java.util.UUID;

public interface ArticleRepositoryPort {
    Optional<Article> findById(UUID articleId, CenterId centerId);

    Article save(Article article);
}

