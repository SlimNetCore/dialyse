package com.hemodialyse.backend.infrastructure.acl.gmao;

import com.hemodialyse.backend.application.planning.GenerateurIndisponibleService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementReforme;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consommateur Planification des événements du parc d'équipements (ADR 0024) : remplace l'appel direct que faisait le
 * contrôleur legacy. Livré après commit, par le registre Spring Modulith (au moins une fois : l'alerte peut se répéter après
 * un redémarrage interrompu).
 * <ul>
 *   <li>Alerte « générateur indisponible » : {@link GenerateurIndisponibleService#signaler} applique la règle
 *       KB-GMAO-37 : générateur de dialyse dont le statut précédent est {@code EN_SERVICE} et qui n'y est plus, par mise hors
 *       service, état constaté ou réforme.</li>
 * </ul>
 * L'éviction du cache {@code ref.generateurs} n'est plus ici : elle est synchrone, par {@code @CacheEvict} sur
 * {@code EquipementControleur} (elle doit précéder la réponse HTTP). Événements non versionnés : cet écouteur dépend des
 * classes d'événements du contexte ; à versionner quand un 2e consommateur ou une séparation de service apparaîtra. Retiré quand Planification aura son contexte et lira les événements à sa façon.
 */
@Component
public class GmaoEvenementsPlanificationListener {

    private final GenerateurIndisponibleService generateurIndisponible;

    public GmaoEvenementsPlanificationListener(GenerateurIndisponibleService generateurIndisponible) {
        this.generateurIndisponible = generateurIndisponible;
    }

    @ApplicationModuleListener
    void surEquipementMisHorsService(EquipementMisHorsService evenement) {
        signaler(evenement.type().name(), evenement.code().valeur(), evenement.equipementId().valeur(),
                evenement.centreId().valeur(), evenement.statut().name(), evenement.statutPrecedent().name());
    }

    @ApplicationModuleListener
    void surEquipementReforme(EquipementReforme evenement) {
        signaler(evenement.type().name(), evenement.code().valeur(), evenement.equipementId().valeur(),
                evenement.centreId().valeur(), "REFORME", evenement.statutPrecedent().name());
    }

    private void signaler(String type, String code, UUID equipementId, UUID centreId, String statut, String statutPrecedent) {
        generateurIndisponible.signaler(type, centreId, equipementId, code, statut, statutPrecedent);
    }
}
