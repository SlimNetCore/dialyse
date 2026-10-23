package com.hemodialyse.backend.contexts.stock.doubles;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.PageDArticles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Pagination;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Double en mémoire du port {@link Articles} (même contrat que {@code ArticlesJdbc}).
 */
public final class ArticlesEnMemoire implements Articles {

    private final Map<UUID, Article> parId = new LinkedHashMap<>();

    private static boolean contient(String valeur, String texte) {
        return valeur != null && valeur.toLowerCase(Locale.ROOT).contains(texte);
    }

    private Stream<Article> du(CentreId centre) {
        return parId.values().stream().filter(a -> a.centre().equals(centre))
                .sorted(Comparator.comparing(a -> a.fiche().code()));
    }

    @Override
    public Optional<Article> parId(CentreId centre, UUID id) {
        return Optional.ofNullable(parId.get(id)).filter(a -> a.centre().equals(centre));
    }

    @Override
    public Optional<Article> parCode(CentreId centre, String code) {
        return du(centre).filter(a -> a.fiche().code().equalsIgnoreCase(code.trim())).findFirst();
    }

    @Override
    public void enregistrer(Article article) {
        parId.put(article.id(), article);
    }

    @Override
    public PageDArticles rechercher(CentreId centre, String texte, Boolean actif, Pagination pagination) {
        String t = texte == null || texte.isBlank() ? null : texte.trim().toLowerCase(Locale.ROOT);
        List<Article> trouves = du(centre)
                .filter(a -> actif == null || a.actif() == actif)
                .filter(a -> t == null || contient(a.fiche().code(), t) || contient(a.fiche().libelle(), t)
                        || contient(a.fiche().dci(), t) || contient(a.fiche().codeBarres(), t))
                .toList();
        List<Article> page = trouves.stream().skip(pagination.decalage()).limit(pagination.taille()).toList();
        return new PageDArticles(page, trouves.size(), pagination);
    }

    @Override
    public List<Article> tous(CentreId centre) {
        return du(centre).toList();
    }

    @Override
    public List<Article> parTypeDeTraitement(CentreId centre, TypeTraitementAnemie type) {
        return du(centre).filter(a -> a.fiche().typeTraitementAnemie() == type).toList();
    }

    @Override
    public java.util.Set<UUID> identifiants(CentreId centre) {
        return du(centre).map(Article::id).collect(java.util.stream.Collectors.toSet());
    }
}


