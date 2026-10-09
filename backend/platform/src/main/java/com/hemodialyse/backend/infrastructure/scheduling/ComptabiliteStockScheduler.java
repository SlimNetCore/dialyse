package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.comptabilite.ComptabiliteStockApplicationService;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase.Synchronisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Job quotidien : met la comptabilité de chaque centre en accord avec son stock (réceptions validées, sorties du jour,
 * inventaires clôturés). La fenêtre glissante rattrape les mouvements saisis après coup et un arrêt du serveur ; le
 * traitement se rejoue sans doublon. Une période plus ancienne se comptabilise à la demande, depuis l'écran.
 */
@Component
public class ComptabiliteStockScheduler {

    /**
     * Fenêtre glissante : le mois en cours et la fin du précédent, tant qu'il n'est pas clôturé.
     */
    static final int JOURS_A_COMPTABILISER = 35;
    private static final Logger log = LoggerFactory.getLogger(ComptabiliteStockScheduler.class);

    private final ComptabiliteStockApplicationService service;

    public ComptabiliteStockScheduler(ComptabiliteStockApplicationService service) {
        this.service = service;
    }

    /**
     * Tous les jours à 03:15.
     */
    @Scheduled(cron = "0 15 3 * * *")
    public void comptabiliserStock() {
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        for (UUID centre : service.centres()) {
            comptabiliserCentre(centre, aujourdhui);
        }
    }

    void comptabiliserCentre(UUID centre, LocalDate aujourdhui) {
        try {
            Synchronisation s = service.synchroniser(centre, aujourdhui.minusDays(JOURS_A_COMPTABILISER), aujourdhui,
                    aujourdhui);
            if (s.ignorees() > 0) {
                log.warn("[COMPTA-STOCK] Centre {} : {} pièce(s) non comptabilisée(s), période clôturée", centre,
                        s.ignorees());
            }
        } catch (RuntimeException e) {
            // un centre en erreur ne doit pas empêcher la comptabilisation des autres
            log.warn("[COMPTA-STOCK] Comptabilisation du stock impossible pour le centre {}", centre, e);
        }
    }
}
