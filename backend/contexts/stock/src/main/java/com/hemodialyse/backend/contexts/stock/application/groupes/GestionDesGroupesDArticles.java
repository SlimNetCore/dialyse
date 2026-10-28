package com.hemodialyse.backend.contexts.stock.application.groupes;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupesDArticles;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupesDArticles.PageDeGroupes;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage des groupes d'articles d'un centre (RG-STK-070 à 072) : créer, modifier, supprimer, consulter, lister ; lecture
 * des groupes de plusieurs centres pour la direction (RG-STK-073). Ordre des contrôles du legacy : composition (domaine), nom
 * libre, articles du centre.
 */
public final class GestionDesGroupesDArticles {

    private final GroupesDArticles groupes;
    private final Articles articles;
    private final Supplier<UUID> identifiants;
    private final Clock horloge;

    public GestionDesGroupesDArticles(GroupesDArticles groupes, Articles articles, Supplier<UUID> identifiants, Clock horloge) {
        this.groupes = groupes;
        this.articles = articles;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public PageDeGroupes lister(CentreId centre, int page, int taille) {
        return groupes.page(centre, page, taille);
    }

    /**
     * @throws RefusDuReferentiel {@code GROUPE_ARTICLE_INTROUVABLE} (422, comme le legacy) : inconnu ou d'un autre centre
     */
    public GroupeDArticles consulter(CentreId centre, UUID id) {
        return groupes.parId(centre, id).orElseThrow(
                () -> new RefusDuReferentiel("GROUPE_ARTICLE_INTROUVABLE", "Groupe d'articles introuvable"));
    }

    public GroupeDArticles creer(CentreId centre, String nom, String description, Collection<UUID> composition) {
        GroupeDArticles groupe = GroupeDArticles.composer(identifiants.get(), centre, nom, description, composition, maintenant());
        exigerNomLibre(centre, groupe.cle(), null);
        exigerArticlesDuCentre(centre, groupe);
        groupes.enregistrer(groupe);
        return groupe;
    }

    public GroupeDArticles modifier(CentreId centre, UUID id, String nom, String description, Collection<UUID> composition) {
        GroupeDArticles modifie = consulter(centre, id).modifier(nom, description, composition, maintenant());
        exigerNomLibre(centre, modifie.cle(), id);
        exigerArticlesDuCentre(centre, modifie);
        groupes.enregistrer(modifie);
        return modifie;
    }

    public void supprimer(CentreId centre, UUID id) {
        consulter(centre, id);
        groupes.supprimer(centre, id);
    }

    public List<GroupeDArticles> desCentres(Collection<CentreId> centres) {
        return centres == null || centres.isEmpty() ? List.of() : groupes.desCentres(centres);
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.now(horloge);
    }

    private void exigerNomLibre(CentreId centre, String cle, UUID sauf) {
        if (groupes.nomPris(centre, cle, sauf)) {
            throw new RefusDuReferentiel("GROUPE_ARTICLE_NOM_EXISTANT", "Un groupe porte déjà ce nom dans ce centre");
        }
    }

    /**
     * Isolation multi-centre : un article inconnu ou d'un autre centre est refusé (articles inactifs acceptés).
     */
    private void exigerArticlesDuCentre(CentreId centre, GroupeDArticles groupe) {
        if (!articles.identifiants(centre).containsAll(groupe.articles())) {
            throw new RefusDuReferentiel("GROUPE_ARTICLE_ARTICLE_INCONNU",
                    "Le groupe contient un article qui n'appartient pas à ce centre");
        }
    }
}

