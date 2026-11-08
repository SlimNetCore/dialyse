package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Lecture des raccourcis de consommables du centre (GET /api/v1/seances/raccourcis-consommables, administrateur, infirmier, secrétaire) :
 * le centre de la session (ou celui demandé s'il est le sien). La mise en cache par centre est un détail de l'adapter (KB-SEA-32).
 */
public class ConsulterLesRaccourcis {

    private final SessionCourante session;
    private final RaccourcisDesCentres raccourcis;

    public ConsulterLesRaccourcis(SessionCourante session, RaccourcisDesCentres raccourcis) {
        this.session = session;
        this.raccourcis = raccourcis;
    }

    public RaccourcisDeConsommables executer(UUID centreDemande) {
        CentreId centre = session.centre(centreDemande);
        return raccourcis.du(centre);
    }
}
