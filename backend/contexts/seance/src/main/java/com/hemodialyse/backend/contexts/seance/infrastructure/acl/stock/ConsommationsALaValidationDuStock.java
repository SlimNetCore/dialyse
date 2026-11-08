package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee;
import com.hemodialyse.backend.contexts.seance.application.ConsommationsALaValidation;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL vers le langage publié du Stock : l'article du catalogue ({@link CatalogueDArticles}) et les consommables relevés à la validation
 * ({@link ConsommationsDeSeance#enregistrerALaValidation}). Le Stock décide (imputation sur la sortie pour séances ou sortie FEFO) ; un refus
 * du Stock devient une {@link ConsommationRefusee} avec le code du Stock.
 */
public class ConsommationsALaValidationDuStock implements ConsommationsALaValidation {

    private final CatalogueDArticles catalogue;
    private final ConsommationsDeSeance stock;

    public ConsommationsALaValidationDuStock(CatalogueDArticles catalogue, ConsommationsDeSeance stock) {
        this.catalogue = catalogue;
        this.stock = stock;
    }

    @Override
    public Optional<Article> article(CentreId centre, UUID article) {
        return catalogue.article(centre.valeur(), article).map(a -> new Article(a.id(), a.code(), a.actif()));
    }

    @Override
    public void enregistrer(CentreId centre, Seance seance, List<Ligne> lignes, String auteur) {
        try {
            stock.enregistrerALaValidation(centre.valeur(), seance.id().valeur(), seance.patient().valeur(), seance.jour(),
                    lignes.stream().map(l -> new ConsommationsDeSeance.Ligne(l.article(), l.quantite())).toList(), auteur);
        } catch (com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee refus) {
            throw new ConsommationRefusee(refus.code(), refus.getMessage());
        }
    }
}
