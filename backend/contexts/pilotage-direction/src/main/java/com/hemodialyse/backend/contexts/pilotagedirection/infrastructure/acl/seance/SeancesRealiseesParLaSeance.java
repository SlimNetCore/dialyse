package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SeancesRealiseesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeSeances;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.SeancesDeLaCaisse;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ACL : les séances réalisées des centres lues dans {@code seance.v1} ({@link SeancesRealiseesDesCentresPubliees}, SE7e, ADR 0099). */
public final class SeancesRealiseesParLaSeance implements SeancesRealiseesDesCentres {

    private final SeancesRealiseesDesCentresPubliees seance;

    public SeancesRealiseesParLaSeance(SeancesRealiseesDesCentresPubliees seance) {
        this.seance = seance;
    }

    @Override
    public Map<UUID, Long> parCentre(List<UUID> centres, Periode periode) {
        return new HashMap<>(seance.parCentre(centres, periode.debut(), periode.fin()));
    }

    @Override
    public long auTotal(List<UUID> centres, Periode periode) {
        return seance.auTotal(centres, periode.debut(), periode.fin());
    }

    @Override
    public List<MoisDeSeances> parMois(List<UUID> centres, Periode periode) {
        return seance.parMois(centres, periode.debut(), periode.fin()).stream()
                .map(m -> new MoisDeSeances(m.centre(), m.annee(), m.mois(), m.nombre())).toList();
    }

    @Override
    public List<SeancesDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
        return seance.parCaisse(centres, periode.debut(), periode.fin()).stream()
                .map(c -> new SeancesDeLaCaisse(c.centre(), c.codeCaisse(), c.nomCaisse(), c.nombre())).toList();
    }
}
