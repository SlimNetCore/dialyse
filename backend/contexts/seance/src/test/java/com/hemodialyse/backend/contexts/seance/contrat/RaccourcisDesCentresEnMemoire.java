package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Double en mémoire du port {@link RaccourcisDesCentres} : le même contrat que la base (H2, PostgreSQL 16) lui est appliqué. */
public class RaccourcisDesCentresEnMemoire implements RaccourcisDesCentres {

    private final Map<CentreId, List<ArticleId>> parCentre = new HashMap<>();
    /** Nombre de remplacements reçus (un refus de composition n'en provoque aucun). */
    public int remplacements;

    @Override
    public RaccourcisDeConsommables du(CentreId centre) {
        return RaccourcisDeConsommables.restituer(centre, parCentre.getOrDefault(centre, List.of()));
    }

    @Override
    public void remplacer(RaccourcisDeConsommables raccourcis) {
        remplacements++;
        parCentre.put(raccourcis.centre(), raccourcis.articles());
    }
}
