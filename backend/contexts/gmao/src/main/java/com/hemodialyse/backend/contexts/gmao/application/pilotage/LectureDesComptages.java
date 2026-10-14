package com.hemodialyse.backend.contexts.gmao.application.pilotage;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant de lecture : les comptages du pilotage. Les équipements, interventions et plans supprimés logiquement ne sont
 * jamais comptés. Le statut se compare au code exact stocké.
 */
public interface LectureDesComptages {

    long equipements(CentreId centre);

    long equipementsAuStatut(CentreId centre, StatutEquipement statut);

    long interventions(CentreId centre);

    long interventionsAuStatut(CentreId centre, StatutIntervention statut);

    /** Interventions d'un équipement, sans regarder leur centre (KB-GMAO-71). */
    long interventionsDeLEquipement(EquipementId equipement);

    /**
     * Générateurs de dialyse installés strictement avant {@code avant}, ni réformés, ni désactivés, par centre ; un centre sans
     * générateur est absent.
     */
    Map<UUID, Long> generateursInstallesAvant(Collection<UUID> centres, Instant avant);

    long plansActifs(CentreId centre);

    /** Plans actifs dont la prochaine date prévue est antérieure ou égale à {@code maintenant}. */
    long plansEnRetard(CentreId centre, Instant maintenant);

    /**
     * Interventions à relancer (R27) : planifiées dont l'heure de début est passée, ou planifiées ou en cours dont l'échéance est
     * dépassée.
     */
    long interventionsARelancer(CentreId centre, Instant maintenant);
}
