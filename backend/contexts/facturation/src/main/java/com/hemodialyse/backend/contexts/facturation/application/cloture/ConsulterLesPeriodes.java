package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Requête « états des 12 mois d'une année » (RG-FAC-045) : ouvert ou clos, avec la dernière clôture et la dernière réouverture.
 * Un mois sans ligne est ouvert. Lecture seule.
 */
public final class ConsulterLesPeriodes {

    private final MoisDeFacturationRepository mois;

    public ConsulterLesPeriodes(MoisDeFacturationRepository mois) {
        this.mois = mois;
    }

    public List<MoisDeFacturation> executer(CentreId centre, int annee) {
        Map<YearMonth, MoisDeFacturation> enregistres = new HashMap<>();
        mois.deLAnnee(centre, annee).forEach(m -> enregistres.put(m.mois(), m));
        List<MoisDeFacturation> douzeMois = new ArrayList<>();
        for (int numero = 1; numero <= 12; numero++) {
            YearMonth courant = YearMonth.of(annee, numero);
            douzeMois.add(enregistres.getOrDefault(courant, MoisDeFacturation.ouvert(centre, courant)));
        }
        return douzeMois;
    }
}
