package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.application.ConsommablesDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee;
import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.QuantiteConsommee;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;

import java.math.BigDecimal;
import java.util.Set;

/**
 * ACL vers le langage publié du Stock ({@link ConsommationsDeSeance#ajouter}, {@code fixer}) : la déclaration des consommables de la
 * séance. Le Stock décide (imputation sur la sortie pour séances, alertes) ; les refus sont traduits :
 * <ul>
 *   <li>{@code ARTICLE_INTROUVABLE}, {@code ARTICLE_INACTIF}, {@code QUANTITE_INVALIDE} : {@link SaisieInvalide} (400, KB-SEA-25 : le même
 *   statut que la validation de séance et les raccourcis pour la même cause) ;</li>
 *   <li>tout autre code ({@code SEANCE_FACTUREE_NON_MODIFIABLE}, {@code PIECE_DEJA_RAPPROCHEE}) : {@link ConsommationRefusee} (422, code du
 *   Stock).</li>
 * </ul>
 * Le retrait est une quantité fixée à zéro. L'auteur est transmis tel quel (KB-SEA-33 : le Stock ne l'enregistre pas).
 */
public class ConsommablesDeLaSeanceDuStock implements ConsommablesDeLaSeance {

    /** Codes du Stock qui sont des refus de saisie (la valeur demandée est inutilisable), non des refus de règle de la séance. */
    static final Set<String> CODES_DE_SAISIE_INVALIDE = Set.of("ARTICLE_INTROUVABLE", "ARTICLE_INACTIF", "QUANTITE_INVALIDE");

    private final ConsommationsDeSeance stock;

    public ConsommablesDeLaSeanceDuStock(ConsommationsDeSeance stock) {
        this.stock = stock;
    }

    @Override
    public void ajouter(CentreId centre, Seance seance, ArticleId article, QuantiteConsommee quantite, String auteur) {
        try {
            stock.ajouter(centre.valeur(), seance.id().valeur(), seance.patient().valeur(), seance.jour(), article.valeur(),
                    quantite.valeur(), auteur);
        } catch (com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee refus) {
            throw traduire(refus);
        }
    }

    @Override
    public void remplacer(CentreId centre, Seance seance, ArticleId article, QuantiteConsommee quantite, String auteur) {
        fixer(centre, seance, article, quantite.valeur(), auteur);
    }

    @Override
    public void retirer(CentreId centre, Seance seance, ArticleId article, String auteur) {
        fixer(centre, seance, article, BigDecimal.ZERO, auteur);
    }

    private void fixer(CentreId centre, Seance seance, ArticleId article, BigDecimal quantite, String auteur) {
        try {
            stock.fixer(centre.valeur(), seance.id().valeur(), seance.patient().valeur(), seance.jour(), article.valeur(), quantite,
                    auteur);
        } catch (com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee refus) {
            throw traduire(refus);
        }
    }

    static RuntimeException traduire(com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee refus) {
        if (CODES_DE_SAISIE_INVALIDE.contains(refus.code())) return new SaisieInvalide(refus.getMessage());
        return new ConsommationRefusee(refus.code(), refus.getMessage());
    }
}
