package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;

import java.util.Comparator;
import java.util.List;

/**
 * Implémentation des use cases {@link ReferentielsLectureUseCases}.
 * Compose les langages publiés des contextes propriétaires (Stock, GMAO) :
 * seule dépendance inter-contextes autorisée (langage publié, règle ArchUnit
 * {@code les_autres_contextes_seulement_par_leur_langage_publie}).
 */
public class ReferentielsLectureService implements ReferentielsLectureUseCases {

    private final CatalogueDArticles catalogue;
    private final GenerateursDuCentre generateurs;

    public ReferentielsLectureService(CatalogueDArticles catalogue, GenerateursDuCentre generateurs) {
        this.catalogue = catalogue;
        this.generateurs = generateurs;
    }

    @Override
    public PagedResult<ReferentielsLectureUseCases.ArticleLu> listerArticles(CentreId centreId, int page, int size) {
        List<ReferentielsLectureUseCases.ArticleLu> actifs = catalogue.articles(centreId.valeur()).stream()
                .filter(ArticleDuCatalogue::actif)
                .map(a -> new ReferentielsLectureUseCases.ArticleLu(a.id(), a.code(), a.libelle(), a.unite()))
                .sorted(Comparator.comparing(ReferentielsLectureUseCases.ArticleLu::libelle,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        int from = Math.min(page * size, actifs.size());
        int to = Math.min(from + size, actifs.size());
        return PagedResult.of(actifs.subList(from, to), actifs.size(), page, size);
    }

    @Override
    public List<ReferentielsLectureUseCases.GenerateurLu> listerGenerateurs(CentreId centreId) {
        return generateurs.duCentre(centreId.valeur()).stream()
                .map(g -> new ReferentielsLectureUseCases.GenerateurLu(
                        g.id(), g.code(),
                        g.designation() == null || g.designation().isBlank() ? "Générateur " + g.code() : g.designation(),
                        g.salleId(), g.statut()))
                .toList();
    }
}
