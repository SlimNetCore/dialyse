package com.hemodialyse.backend.contexts.stock.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.consommations.PerimetreDesSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.PerimetreDesSeancesFourni;

import java.util.UUID;

/** Le périmètre des séances du Stock, tel que la Séance le lui fournit ({@link PerimetreDesSeancesFourni}, ADR 0191). Traduction champ à champ. */
public class PerimetreDesSeancesFournis implements PerimetreDesSeances {

    private final PerimetreDesSeancesFourni seances;

    public PerimetreDesSeancesFournis(PerimetreDesSeancesFourni seances) {
        this.seances = seances;
    }

    @Override
    public PerimetreDeSortie perimetreDe(CentreId centre, UUID seance) {
        var perimetre = seances.perimetreDe(centre.valeur(), seance);
        var demi = perimetre.demiJournee();
        return new PerimetreDeSortie(perimetre.salle(), demi == null ? null : DemiJournee.valueOf(demi.name()));
    }
}
