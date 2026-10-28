package com.hemodialyse.backend.contexts.absencespatients.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Requête « états des 12 mois d'une année » (A18) : ouvert ou clos, avec la dernière clôture et la dernière réouverture. Un mois
 * sans ligne est ouvert. Lecture seule.
 */
public class ConsulterLesMoisDAbsences {

    private final MoisAbsencesRepository mois;

    public ConsulterLesMoisDAbsences(MoisAbsencesRepository mois) {
        this.mois = mois;
    }

    public List<MoisAbsences> executer(CentreId centre, int annee) {
        Map<YearMonth, MoisAbsences> enregistres = new HashMap<>();
        mois.deLAnnee(centre, annee).forEach(m -> enregistres.put(m.mois(), m));
        List<MoisAbsences> douzeMois = new ArrayList<>();
        for (int numero = 1; numero <= 12; numero++) {
            YearMonth courant = YearMonth.of(annee, numero);
            douzeMois.add(enregistres.getOrDefault(courant, MoisAbsences.ouvert(centre, courant)));
        }
        return douzeMois;
    }
}
