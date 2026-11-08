package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.ArticlesDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/**
 * Remplacement en bloc des raccourcis de consommables du centre (PUT /api/v1/seances/raccourcis-consommables, administrateur seul). Dans
 * une seule unité de travail : la liste est composée (taille, doublon, article connu et actif) AVANT toute écriture, un refus n'écrit
 * rien ; deux remplacements simultanés du même centre se suivent (409 si le verrou n'est pas obtenu). Aucune diffusion, aucune
 * trace d'auteur. Une liste absente efface tous les raccourcis (KB-SEA-31, reproduit : risque d'effacement par un corps {@code {}}).
 */
public class RemplacerLesRaccourcis {

    private final SessionCourante session;
    private final ArticlesDuCentre articles;
    private final RaccourcisDesCentres raccourcis;
    private final UniteDeTravail uniteDeTravail;

    public RemplacerLesRaccourcis(SessionCourante session, ArticlesDuCentre articles, RaccourcisDesCentres raccourcis,
                                  UniteDeTravail uniteDeTravail) {
        this.session = session;
        this.articles = articles;
        this.raccourcis = raccourcis;
        this.uniteDeTravail = uniteDeTravail;
    }

    /** @param demandes identifiants reçus ({@code null} : liste vide ; un élément peut être nul) */
    public RaccourcisDeConsommables executer(UUID centreDemande, List<UUID> demandes) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-26)
        return uniteDeTravail.executer(() -> {
            RaccourcisDeConsommables nouveaux = RaccourcisDeConsommables.composer(centre, demandes, articles);
            raccourcis.remplacer(nouveaux);
            return nouveaux;
        });
    }
}
