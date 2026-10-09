package com.hemodialyse.backend.domain.comptabilite.port;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port entrant — comptabilisation du stock (inventaire permanent).
 */
public interface ComptabiliteStockUseCase {

    /**
     * Met la comptabilité d'un centre en accord avec son stock sur une période : une écriture par bon de réception
     * validé, une écriture par jour pour les sorties, une écriture par inventaire clôturé. Rejouable sans doublon.
     *
     * @param aujourdhui date du jour, portée par les écritures de complément d'une période déjà clôturée ou exportée
     */
    Synchronisation synchroniser(UUID centerId, LocalDate du, LocalDate au, LocalDate aujourdhui);

    /**
     * @param receptions   écritures de réception créées
     * @param joursSorties écritures journalières de sorties créées ou mises à jour
     * @param inventaires  écritures d'écarts d'inventaire créées
     * @param complements  écritures de complément créées (journée déjà exportée ou période clôturée)
     * @param ignorees     pièces non comptabilisées parce que leur période est clôturée
     */
    record Synchronisation(int receptions, int joursSorties, int inventaires, int complements, int ignorees) {
    }
}
