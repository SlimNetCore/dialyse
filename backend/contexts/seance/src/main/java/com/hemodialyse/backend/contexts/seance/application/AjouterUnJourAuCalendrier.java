package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ajout d'un jour férié ou d'une fermeture exceptionnelle (POST /calendar/holiday et /calendar/closure, administrateur seul). Centre de la
 * session (403 avant 400) ; jour absent, libellé trop long : 400 ; jour déjà présent pour ce centre : 409 (KB-SEA-58 corrigé). Aucun
 * contrôle de cohérence avec les séances déjà posées ce jour-là (reproduit, ADR 0094). L'identifiant est tiré par l'application.
 */
public class AjouterUnJourAuCalendrier {

    private final SessionCourante session;
    private final CalendrierDuCentre calendrier;
    private final GenerateurDIdentifiants identifiants;

    public AjouterUnJourAuCalendrier(SessionCourante session, CalendrierDuCentre calendrier, GenerateurDIdentifiants identifiants) {
        this.session = session;
        this.calendrier = calendrier;
        this.identifiants = identifiants;
    }

    public JourDuCalendrier executer(UUID centreDemande, NatureDeJour nature, LocalDate jour, String libelle) {
        CentreId centre = session.centre(centreDemande);
        JourDuCalendrier ajoute = new JourDuCalendrier(identifiants.suivant(), centre, jour, nature, libelle);
        calendrier.ajouter(ajoute);
        return ajoute;
    }
}
