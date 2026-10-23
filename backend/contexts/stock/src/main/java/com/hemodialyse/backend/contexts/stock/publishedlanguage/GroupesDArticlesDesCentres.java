package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié du Stock : les groupes d'articles de plusieurs centres (lecture de la direction, RG-STK-073, slice S2).
 * Aucune donnée nominative ; l'appelant borne les centres (ceux de la société).
 */
public interface GroupesDArticlesDesCentres {

    /**
     * Tous les groupes des centres donnés (vide si aucun centre).
     */
    List<GroupeDArticlesPublie> groupes(Collection<UUID> centres);

    /**
     * Un groupe tel que le Stock le publie.
     *
     * @param cle clé de consolidation du nom (sans casse, accents ni espaces multiples) : même clé = même groupe d'un centre à
     *            l'autre
     */
    record GroupeDArticlesPublie(UUID id, UUID centre, String nom, String cle, Set<UUID> articles) {
    }
}

