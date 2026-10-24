package com.hemodialyse.backend.contexts.stock.interfaces.catalogue;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.HistoriqueDesMouvements;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.ValorisationHistorique;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ValorisationDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ValorisationsDuStock;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié de la valorisation historique, au-dessus de l'historique des mouvements et de {@link ValorisationHistorique}.
 */
public class ValorisationsDuStockPubliees implements ValorisationsDuStock {
    private final HistoriqueDesMouvements historique;

    public ValorisationsDuStockPubliees(HistoriqueDesMouvements historique) {
        this.historique = historique;
    }

    @Override
    public Map<UUID, ValorisationDuStock> valoriser(UUID centre, Set<UUID> articles, OffsetDateTime debut,
                                                    OffsetDateTime finExclue) {
        Map<UUID, List<MouvementDeStock>> mouvements = historique.parArticle(new CentreId(centre), articles);
        Map<UUID, ValorisationDuStock> valeurs = new HashMap<>();
        for (UUID article : articles) {
            ValorisationHistorique.Valorisation v = ValorisationHistorique.calculer(mouvements.getOrDefault(article, List.of()), debut,
                    finExclue);
            valeurs.put(article, new ValorisationDuStock(v.valeurDebut(), v.entrees(), v.sorties(), v.autresVariations(),
                    v.valeurFin(), v.quantiteFin()));
        }
        return Map.copyOf(valeurs);
    }
}
