package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RapprochementDesAlertes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Compare les alertes actuellement levées d'une société à son historique ouvert : ouvre les nouvelles, referme celles qui ont disparu. Alimenté
 * uniquement quand le temps réel recalcule la société, donc pendant qu'une direction est connectée (KB-DIR-04) : ce n'est pas un audit exhaustif.
 */
public final class EnregistrerLesAlertes {

    private final HistoriqueDesAlertes historique;
    private final HorlogeDuPilotage horloge;

    public EnregistrerLesAlertes(HistoriqueDesAlertes historique, HorlogeDuPilotage horloge) {
        this.historique = historique;
        this.horloge = horloge;
    }

    public void execute(UUID societeId, List<AlerteLevee> levees) {
        execute(societeId, levees, horloge.maintenant());
    }

    public void execute(UUID societeId, List<AlerteLevee> levees, OffsetDateTime a) {
        var rapprochement = RapprochementDesAlertes.rapprocher(historique.ouvertes(societeId), levees);
        for (AlerteLevee alerte : rapprochement.aOuvrir()) {
            historique.ouvrir(societeId, alerte, a);
        }
        for (UUID id : rapprochement.aResoudre()) {
            historique.resoudre(id, a);
        }
    }
}
