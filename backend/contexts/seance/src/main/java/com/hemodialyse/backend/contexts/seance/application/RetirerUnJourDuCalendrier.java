package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Retrait d'un jour férié ou d'une fermeture exceptionnelle (DELETE /calendar/holiday/{id} et /calendar/closure/{id}, administrateur seul).
 * Rend vrai si une ligne a été retirée, faux sinon : le retrait d'un jour inexistant (inconnu, d'une autre nature ou d'un autre centre)
 * n'est PAS une erreur, la route répond toujours 200 (KB-SEA-58, reproduit avec le signalement de l'interface : l'appelant ne le sait pas).
 */
public class RetirerUnJourDuCalendrier {

    private final SessionCourante session;
    private final CalendrierDuCentre calendrier;

    public RetirerUnJourDuCalendrier(SessionCourante session, CalendrierDuCentre calendrier) {
        this.session = session;
        this.calendrier = calendrier;
    }

    public boolean executer(UUID centreDemande, NatureDeJour nature, UUID id) {
        CentreId centre = session.centre(centreDemande);
        return calendrier.retirer(centre, nature, id);
    }
}
