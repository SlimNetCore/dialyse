package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.application.SortiesDeLaSeance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.SortiesValoriseesDesSeances;

import java.util.List;

/**
 * ACL vers le langage publié du Stock ({@link SortiesValoriseesDesSeances}, DOC5a, ADR 0193) : les sorties valorisées d'une séance, dans l'ordre
 * du Stock, traduites en lignes de la Séance pour le cahier de dialyse imprimé.
 */
public class SortiesDeLaSeanceDuStock implements SortiesDeLaSeance {

    private final SortiesValoriseesDesSeances stock;

    public SortiesDeLaSeanceDuStock(SortiesValoriseesDesSeances stock) {
        this.stock = stock;
    }

    @Override
    public List<Sortie> deLaSeance(CentreId centre, SeanceId seance) {
        return stock.pourLaSeance(centre.valeur(), seance.valeur()).stream().map(SortiesDeLaSeanceDuStock::traduire).toList();
    }

    static Sortie traduire(SortiesValoriseesDesSeances.LigneDeSortie l) {
        return new Sortie(l.code(), l.libelle(), l.unite(), l.quantite(), l.prixUnitaire(), l.totalValorise());
    }
}
