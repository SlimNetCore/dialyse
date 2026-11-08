package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Raccourcis de consommables d'un centre (racine d'agrégat, une seule entité) : les articles proposés en un toucher à l'infirmier, dans
 * l'ordre d'affichage. Au plus {@value #MAXIMUM}, sans doublon, tous connus et actifs dans le catalogue du centre. La liste est remplacée
 * en bloc ; une liste absente ou vide efface tous les raccourcis du centre (KB-SEA-31, reproduit : à faire confirmer avant la mise en
 * production, un corps {@code {}} efface tout).
 */
public final class RaccourcisDeConsommables {

    public static final int MAXIMUM = 12;

    private final CentreId centre;
    private final List<ArticleId> articles;

    private RaccourcisDeConsommables(CentreId centre, List<ArticleId> articles) {
        if (centre == null) throw new SaisieInvalide("Centre requis");
        this.centre = centre;
        this.articles = List.copyOf(articles);
    }

    /**
     * Compose la nouvelle liste du centre. Les contrôles se font dans l'ordre du legacy : la taille, puis article par article (nul ou
     * doublon, inconnu ou d'un autre centre, inactif) : le premier refus gagne.
     *
     * @param demandes identifiants demandés tels que reçus ({@code null} : liste vide ; un élément peut être nul)
     */
    public static RaccourcisDeConsommables composer(CentreId centre, List<UUID> demandes, ArticlesDuCentre catalogue) {
        List<UUID> ids = demandes == null ? List.of() : demandes;
        if (ids.size() > MAXIMUM) throw new SaisieInvalide("Au plus " + MAXIMUM + " raccourcis de consommables");
        Set<UUID> vus = new HashSet<>();
        for (UUID id : ids) {
            if (id == null || !vus.add(id)) throw new SaisieInvalide("Article en double ou invalide dans les raccourcis");
            ArticleDuCentre article = catalogue.trouver(centre, new ArticleId(id))
                    .orElseThrow(() -> new SaisieInvalide("Article introuvable: " + id));
            if (!article.actif()) throw new SaisieInvalide("Article inactif: " + article.code());
        }
        return new RaccourcisDeConsommables(centre, ids.stream().map(ArticleId::new).toList());
    }

    /** Restitue la liste lue en base (adapter de persistance) : aucune règle du catalogue n'est rejouée (un article a pu être désactivé). */
    public static RaccourcisDeConsommables restituer(CentreId centre, List<ArticleId> articles) {
        return new RaccourcisDeConsommables(centre, articles);
    }

    public CentreId centre() {
        return centre;
    }

    /** Les articles, dans l'ordre d'affichage. */
    public List<ArticleId> articles() {
        return articles;
    }
}
