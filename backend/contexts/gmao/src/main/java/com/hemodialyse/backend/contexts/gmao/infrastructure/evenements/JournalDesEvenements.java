package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.EvenementDEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.EvenementDeIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.EvenementDePieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.EvenementDePlanMaintenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Consommateur minimal : journalise chaque événement publié et l'acquitte. Le seul consommateur métier est l'écouteur de
 * {@code platform} (alerte Planification, événements du parc) ; Pilotage direction consommera {@code InterventionTerminee} (publiée par G4, sans consommateur pour l'instant).
 * Sans cet acquittement, le registre {@code event_publication} n'enregistrerait rien. Une fois acquittée, la publication est supprimée du registre
 * ({@code spring.modulith.events.completion-mode=delete}) : rien ne s'accumule.
 */
public class JournalDesEvenements {

    private static final Logger JOURNAL = LoggerFactory.getLogger(JournalDesEvenements.class);

    @ApplicationModuleListener
    void surEvenementDePlan(EvenementDePlanMaintenance evenement) {
        JOURNAL.debug("GMAO : {}", evenement);
    }

    @ApplicationModuleListener
    void surEvenementDEquipement(EvenementDEquipement evenement) {
        JOURNAL.debug("GMAO : {}", evenement);
    }

    @ApplicationModuleListener
    void surEvenementDIntervenant(EvenementDeIntervenant evenement) {
        JOURNAL.debug("GMAO : {}", evenement);
    }

    @ApplicationModuleListener
    void surEvenementDePieceJointe(EvenementDePieceJointe evenement) {
        JOURNAL.debug("GMAO : {}", evenement);
    }

    @ApplicationModuleListener
    void surEvenementDIntervention(EvenementDIntervention evenement) {
        JOURNAL.debug("GMAO : {}", evenement);
    }
}
