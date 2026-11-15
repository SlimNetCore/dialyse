package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.StatutDesSeancesFourni;

import java.util.UUID;

/**
 * ACL du Stock vers la Séance (ADR 0096, SE7c1) : une séance est facturée quand la Séance le publie ({@link FacturationDesSeancesPubliee}).
 * Remplace {@code StatutDesSeancesLegacy} (SQL direct sur {@code seances}). Hébergé par la Séance, qui dépend du Stock : elle fournit au Stock
 * son port {@link StatutDesSeancesFourni} (ADR 0191).
 */
public class StatutDesSeancesParLaSeance implements StatutDesSeancesFourni {

    private final FacturationDesSeancesPubliee seances;

    public StatutDesSeancesParLaSeance(FacturationDesSeancesPubliee seances) {
        this.seances = seances;
    }

    @Override
    public boolean estFacturee(UUID centre, UUID seanceId) {
        return seances.estFacturee(centre, seanceId);
    }
}
