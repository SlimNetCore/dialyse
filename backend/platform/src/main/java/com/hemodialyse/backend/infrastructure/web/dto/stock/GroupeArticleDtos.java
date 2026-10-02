package com.hemodialyse.backend.infrastructure.web.dto.stock;

import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de l'administration des groupes d'articles.
 */
public final class GroupeArticleDtos {

    private GroupeArticleDtos() {
    }

    public record GroupeArticleRequest(
            @NotBlank(message = "Nom du groupe requis")
            @Size(max = GroupeArticle.NOM_MAX, message = "Nom trop long")
            String nom,

            @Size(max = GroupeArticle.DESCRIPTION_MAX, message = "Description trop longue")
            String description,

            @NotEmpty(message = "Un groupe doit contenir au moins un article")
            @Size(max = GroupeArticle.ARTICLES_MAX, message = "Trop d'articles dans le groupe")
            List<UUID> articleIds
    ) {
    }

    public record GroupeArticleResponse(
            UUID id,
            String nom,
            String description,
            int nbArticles,
            List<UUID> articleIds,
            OffsetDateTime updatedAt
    ) {
        public static GroupeArticleResponse of(GroupeArticle g) {
            return new GroupeArticleResponse(g.id(), g.nom(), g.description(), g.articleIds().size(),
                    g.articleIds().stream().sorted().toList(), g.updatedAt());
        }
    }
}
