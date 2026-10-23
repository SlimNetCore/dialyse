package com.hemodialyse.backend.contexts.stock.doubles;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupesDArticles;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire du port {@link GroupesDArticles} (même contrat que {@code GroupesDArticlesJdbc}).
 */
public final class GroupesDArticlesEnMemoire implements GroupesDArticles {

    private final Map<UUID, GroupeDArticles> parId = new LinkedHashMap<>();

    @Override
    public void enregistrer(GroupeDArticles groupe) {
        parId.put(groupe.id(), groupe);
    }

    @Override
    public Optional<GroupeDArticles> parId(CentreId centre, UUID id) {
        return Optional.ofNullable(parId.get(id)).filter(g -> g.centre().equals(centre));
    }

    @Override
    public PageDeGroupes page(CentreId centre, int page, int taille) {
        List<GroupeDArticles> du = parId.values().stream().filter(g -> g.centre().equals(centre))
                .sorted(Comparator.comparing(GroupeDArticles::cle)).toList();
        return new PageDeGroupes(du.stream().skip((long) page * taille).limit(taille).toList(), du.size(), page, taille);
    }

    @Override
    public boolean nomPris(CentreId centre, String cle, UUID sauf) {
        return parId.values().stream().anyMatch(g -> g.centre().equals(centre) && g.cle().equals(cle) && !g.id().equals(sauf));
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        parId(centre, id).ifPresent(g -> parId.remove(id));
    }

    @Override
    public List<GroupeDArticles> desCentres(Collection<CentreId> centres) {
        return parId.values().stream().filter(g -> centres.contains(g.centre())).toList();
    }
}

