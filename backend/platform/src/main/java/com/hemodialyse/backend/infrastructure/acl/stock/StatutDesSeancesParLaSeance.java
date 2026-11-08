package com.hemodialyse.backend.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ACL du Stock vers la Séance (ADR 0096, SE7c1) : une séance est facturée quand la Séance le publie ({@link FacturationDesSeancesPubliee}).
 * Remplace {@code StatutDesSeancesLegacy} (SQL direct sur {@code seances}). Logée dans la plateforme : le module Seance dépend du module
 * Stock, donc Stock ne peut pas dépendre de Seance (cycle Maven).
 */
@Component
public class StatutDesSeancesParLaSeance implements StatutDesSeances {

    private final FacturationDesSeancesPubliee seances;

    public StatutDesSeancesParLaSeance(FacturationDesSeancesPubliee seances) {
        this.seances = seances;
    }

    @Override
    public boolean estFacturee(CentreId centre, UUID seanceId) {
        return seances.estFacturee(centre.valeur(), seanceId);
    }
}
