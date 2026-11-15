package com.hemodialyse.backend.contexts.stock.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.StatutDesSeancesFourni;

import java.util.UUID;

/** Le statut des séances du Stock, tel que la Séance le lui fournit ({@link StatutDesSeancesFourni}, ADR 0191). Délégation pure. */
public class StatutDesSeancesFournies implements StatutDesSeances {

    private final StatutDesSeancesFourni seances;

    public StatutDesSeancesFournies(StatutDesSeancesFourni seances) {
        this.seances = seances;
    }

    @Override
    public boolean estFacturee(CentreId centre, UUID seanceId) {
        return seances.estFacturee(centre.valeur(), seanceId);
    }
}
