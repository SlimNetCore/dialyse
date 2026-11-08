package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/** Jours fériés et fermetures exceptionnelles d'un mois du calendrier du centre (GET /calendar). Centre de la session (403 avant 400). */
public class ConsulterLeCalendrier {

    private final SessionCourante session;
    private final CalendrierDuCentre calendrier;

    public ConsulterLeCalendrier(SessionCourante session, CalendrierDuCentre calendrier) {
        this.session = session;
        this.calendrier = calendrier;
    }

    public Mois executer(UUID centreDemande, int annee, int mois) {
        CentreId centre = session.centre(centreDemande);
        MoisDuCalendrier demande = new MoisDuCalendrier(annee, mois);
        return new Mois(demande, calendrier.duMois(centre, NatureDeJour.JOUR_FERIE, demande),
                calendrier.duMois(centre, NatureDeJour.FERMETURE_EXCEPTIONNELLE, demande));
    }

    public record Mois(MoisDuCalendrier mois, List<JourDuCalendrier> joursFeries, List<JourDuCalendrier> fermetures) {
    }
}
