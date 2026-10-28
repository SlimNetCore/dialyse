package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié du Stock : indicateurs agrégés par centre pour le pilotage direction (articles actifs, sous seuil,
 * valeur stock au PMP, lots périmés et proches de péremption). Open Host Service, slice S9.
 */
public interface IndicateursDuStock {

    /**
     * Indicateurs par centre pour la liste de centres donnée (centres inconnus absents du résultat).
     */
    Map<UUID, Indicateur> pourCentres(List<UUID> centres, LocalDate aujourdhui);

    record Indicateur(long articlesActifs, long articlesSousSeuil, BigDecimal valeurStock, long lotsPerimes,
                      long lotsProches) {
    }
}
