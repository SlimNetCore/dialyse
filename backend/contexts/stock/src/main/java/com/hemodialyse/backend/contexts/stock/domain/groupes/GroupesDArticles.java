package com.hemodialyse.backend.contexts.stock.domain.groupes;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : les groupes d'articles (tables {@code groupes_articles}, {@code groupes_articles_items}).
 */
public interface GroupesDArticles {

    /**
     * Enregistre le groupe et remplace toute sa composition (en une seule fois).
     */
    void enregistrer(GroupeDArticles groupe);

    /**
     * Le groupe du centre ; vide s'il est inconnu ou d'un autre centre.
     */
    Optional<GroupeDArticles> parId(CentreId centre, UUID id);

    /**
     * Une page des groupes du centre, triés par clé de nom.
     */
    PageDeGroupes page(CentreId centre, int page, int taille);

    /**
     * Un autre groupe du centre (que {@code sauf}, qui peut être nul) porte-t-il déjà cette clé de nom ?
     */
    boolean nomPris(CentreId centre, String cle, UUID sauf);

    /**
     * Supprime le groupe du centre et sa composition.
     */
    void supprimer(CentreId centre, UUID id);

    /**
     * Tous les groupes des centres donnés (lecture de la direction, bornée aux centres de la société).
     */
    List<GroupeDArticles> desCentres(Collection<CentreId> centres);

    /**
     * Une page de groupes et le nombre total de groupes du centre.
     */
    record PageDeGroupes(List<GroupeDArticles> groupes, long total, int page, int taille) {
    }
}

