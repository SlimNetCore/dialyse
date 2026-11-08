package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.application.SortiesDuJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;

import java.time.LocalDate;
import java.util.List;

/**
 * ACL vers le langage publié du Stock ({@link ConsommationsDeSeance#sortiesParDate}) : les articles sortis un jour donné, cumulés par
 * article. Traduction terme à terme (article, code, libellé, quantité totale) ; l'ordre du Stock est conservé.
 */
public class SortiesDuJourDuStock implements SortiesDuJour {

    private final ConsommationsDeSeance stock;

    public SortiesDuJourDuStock(ConsommationsDeSeance stock) {
        this.stock = stock;
    }

    @Override
    public List<SortieDArticle> du(CentreId centre, LocalDate jour) {
        return stock.sortiesParDate(centre.valeur(), jour).stream().map(SortiesDuJourDuStock::traduire).toList();
    }

    public static SortieDArticle traduire(ConsommationsDeSeance.SortieParDate sortie) {
        return new SortieDArticle(sortie.articleId(), sortie.articleCode(), sortie.articleLibelle(), sortie.quantiteTotale());
    }
}
