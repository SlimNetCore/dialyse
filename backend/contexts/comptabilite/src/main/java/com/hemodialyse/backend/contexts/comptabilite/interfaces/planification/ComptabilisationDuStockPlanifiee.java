package com.hemodialyse.backend.contexts.comptabilite.interfaces.planification;

import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock.Synchronisation;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Adaptateur entrant planifié de la slice C6 : tous les jours à 03:15, met la comptabilité de chaque centre en accord avec
 * son stock sur une fenêtre glissante (rattrapage des mouvements saisis après coup et d'un arrêt du serveur). Rejouable
 * sans doublon ; un centre en erreur n'empêche pas les autres ; chaque échec rapporté est journalisé.
 */
public class ComptabilisationDuStockPlanifiee {

    /**
     * Fenêtre glissante : le mois en cours et la fin du précédent, tant qu'il n'est pas clos.
     */
    static final int JOURS_A_COMPTABILISER = 35;
    private static final Logger log = LoggerFactory.getLogger(ComptabilisationDuStockPlanifiee.class);

    private final ComptabiliserLeStock comptabilisation;
    private final Clock horloge;

    public ComptabilisationDuStockPlanifiee(ComptabiliserLeStock comptabilisation, Clock horloge) {
        this.comptabilisation = comptabilisation;
        this.horloge = horloge;
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void comptabiliserLeStock() {
        LocalDate aujourdhui = LocalDate.now(horloge);
        for (CentreId centre : comptabilisation.centres()) {
            comptabiliserCentre(centre, aujourdhui);
        }
    }

    void comptabiliserCentre(CentreId centre, LocalDate aujourdhui) {
        try {
            Synchronisation s = comptabilisation.synchroniser(centre, aujourdhui.minusDays(JOURS_A_COMPTABILISER),
                    aujourdhui, aujourdhui);
            s.echecs().forEach(e -> log.warn("[COMPTA-STOCK] Centre {} : {} {} non comptabilisé(e) ({}) : {}",
                    centre.valeur(), e.nature(), e.reference(), e.code(), e.message()));
        } catch (RuntimeException e) {
            // un centre en erreur ne doit pas empêcher la comptabilisation des autres
            log.warn("[COMPTA-STOCK] Comptabilisation du stock impossible pour le centre {}", centre.valeur(), e);
        }
    }
}

