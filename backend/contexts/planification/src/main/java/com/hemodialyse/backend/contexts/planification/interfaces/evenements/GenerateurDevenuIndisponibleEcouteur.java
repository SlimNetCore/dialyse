package com.hemodialyse.backend.contexts.planification.interfaces.evenements;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateurDevenuIndisponible;
import com.hemodialyse.backend.contexts.planification.application.AlerterUnGenerateurIndisponible;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Adapter entrant : consomme l'événement publié {@code gmao.v1} {@link GenerateurDevenuIndisponible} (livré après commit, au moins
 * une fois, rejoué si l'alerte échoue) et alerte l'administration et le secrétariat. Remplace {@code GmaoEvenementsPlanificationListener}
 * du legacy, qui dépendait des événements de domaine de GMAO. Idempotent : l'alerte a une clé (générateur, jour du centre). Aucun
 * déplacement de séance (KB-PLN-63).
 */
public class GenerateurDevenuIndisponibleEcouteur {

    private final AlerterUnGenerateurIndisponible alerter;

    public GenerateurDevenuIndisponibleEcouteur(AlerterUnGenerateurIndisponible alerter) {
        this.alerter = alerter;
    }

    @ApplicationModuleListener
    void surGenerateurDevenuIndisponible(GenerateurDevenuIndisponible evenement) {
        alerter.executer(new CentreId(evenement.centreId()), new GenerateurId(evenement.equipementId()), evenement.code(),
                evenement.statutPrecedent(), evenement.statut(), evenement.survenuLe());
    }
}
