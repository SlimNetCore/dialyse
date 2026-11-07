package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.application.ConsommationsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;

import java.util.List;

/**
 * ACL vers le langage publié du Stock ({@code ConsommationsDeSeance}) : l'historique des consommables imputés sur la séance, dans l'ordre
 * du Stock, traduit en lignes de la Séance. Le Stock décide des lots (FEFO) et de la valorisation ; la Séance ne fait que lire.
 */
public class ConsommationsDeLaSeanceDuStock implements ConsommationsDeLaSeance {

    private final ConsommationsDeSeance stock;

    public ConsommationsDeLaSeanceDuStock(ConsommationsDeSeance stock) {
        this.stock = stock;
    }

    @Override
    public List<Ligne> historique(CentreId centre, SeanceId seance) {
        return stock.historique(centre.valeur(), seance.valeur()).stream().map(ConsommationsDeLaSeanceDuStock::traduire).toList();
    }

    static Ligne traduire(ConsommationsDeSeance.LigneHistorique l) {
        return new Ligne(l.articleId(), l.articleCode(), l.articleLibelle(), l.articleUnite(), l.quantite(), l.valeurUnitaire(),
                l.totalValorise());
    }
}
