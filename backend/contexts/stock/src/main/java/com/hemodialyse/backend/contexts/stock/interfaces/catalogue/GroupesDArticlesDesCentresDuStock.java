package com.hemodialyse.backend.contexts.stock.interfaces.catalogue;

import com.hemodialyse.backend.contexts.stock.application.groupes.GestionDesGroupesDArticles;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.GroupesDArticlesDesCentres;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter entrant fourni aux autres contextes : le langage publié {@link GroupesDArticlesDesCentres}, servi par les cas
 * d'usage des groupes. Lecture seule, sans garde de session (la direction a déjà borné les centres à sa société).
 */
public class GroupesDArticlesDesCentresDuStock implements GroupesDArticlesDesCentres {

    private final GestionDesGroupesDArticles groupes;

    public GroupesDArticlesDesCentresDuStock(GestionDesGroupesDArticles groupes) {
        this.groupes = groupes;
    }

    @Override
    public List<GroupeDArticlesPublie> groupes(Collection<UUID> centres) {
        if (centres == null || centres.isEmpty()) {
            return List.of();
        }
        return groupes.desCentres(centres.stream().map(CentreId::new).toList()).stream()
                .map(g -> new GroupeDArticlesPublie(g.id(), g.centre().valeur(), g.nom(), g.cle(), Set.copyOf(g.articles())))
                .toList();
    }
}

