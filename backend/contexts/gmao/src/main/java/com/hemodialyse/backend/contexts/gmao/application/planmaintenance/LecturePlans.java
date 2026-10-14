package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port sortant de lecture (read model) des plans de maintenance. Contrat observé du legacy, reproduit à l'identique :
 * {@code parId} ignore la suppression logique (KB-GMAO-17) ; les listes excluent les plans supprimés
 * logiquement ; {@code deEquipement} ignore le statut ; l'ordre des listes n'est pas garanti (KB-GMAO-20). Le filtre de
 * centre des lectures par identité est appliqué par les cas d'usage (KB-GMAO-15 corrigé).
 */
public interface LecturePlans {

    Optional<PlanMaintenanceVue> parId(PlanMaintenanceId id);

    List<PlanMaintenanceVue> duCentre(CentreId centre);

    List<PlanMaintenanceVue> actifsDuCentre(CentreId centre);

    /** Plans actifs dont la prochaine date prévue est antérieure ou égale à {@code maintenant}. */
    List<PlanMaintenanceVue> enRetardDuCentre(CentreId centre, Instant maintenant);

    List<PlanMaintenanceVue> deEquipement(EquipementId equipement);
}
