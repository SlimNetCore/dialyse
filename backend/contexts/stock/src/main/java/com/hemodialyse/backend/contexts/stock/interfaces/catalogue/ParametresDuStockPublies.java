package com.hemodialyse.backend.contexts.stock.interfaces.catalogue;

import com.hemodialyse.backend.contexts.stock.application.parametres.ParametrerLeStock;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ParametresDuStockDuCentre;

import java.util.UUID;

/**
 * Langage publié du paramétrage du stock d'un centre, au-dessus du cas d'usage de paramétrage.
 */
public class ParametresDuStockPublies implements ParametresDuStockDuCentre {
    private final ParametrerLeStock parametrage;

    public ParametresDuStockPublies(ParametrerLeStock parametrage) {
        this.parametrage = parametrage;
    }

    @Override
    public boolean sortiePourSeancesActivee(UUID centre) {
        return parametrage.sortiePourSeancesActivee(new CentreId(centre));
    }
}