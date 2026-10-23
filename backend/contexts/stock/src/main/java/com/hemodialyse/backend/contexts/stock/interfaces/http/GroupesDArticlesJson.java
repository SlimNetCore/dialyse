package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupesDArticles.PageDeGroupes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JSON des groupes d'articles, identique au legacy ({@code GroupeArticleDtos}, {@code PagedResult}) : mêmes champs, mêmes
 * contrôles de forme (400 {@code VALIDATION_ERROR}, appliqués à la saisie brute, avant nettoyage).
 */
final class GroupesDArticlesJson {

    private GroupesDArticlesJson() {
    }

    record GroupeRequete(
            @NotBlank(message = "Nom du groupe requis")
            @Size(max = GroupeDArticles.NOM_MAX, message = "Nom trop long")
            String nom,

            @Size(max = GroupeDArticles.DESCRIPTION_MAX, message = "Description trop longue")
            String description,

            @NotEmpty(message = "Un groupe doit contenir au moins un article")
            @Size(max = GroupeDArticles.ARTICLES_MAX, message = "Trop d'articles dans le groupe")
            List<UUID> articleIds
    ) {
    }

    record GroupeReponse(UUID id, String nom, String description, int nbArticles, List<UUID> articleIds,
                         OffsetDateTime updatedAt) {
        static GroupeReponse depuis(GroupeDArticles g) {
            return new GroupeReponse(g.id(), g.nom(), g.description(), g.articles().size(), List.copyOf(g.articles()),
                    g.modifieLe());
        }
    }

    record PageReponse(List<GroupeReponse> items, long total, int page, int size) {
        static PageReponse depuis(PageDeGroupes page) {
            return new PageReponse(page.groupes().stream().map(GroupeReponse::depuis).toList(), page.total(), page.page(),
                    page.taille());
        }
    }
}

