package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.util.Optional;

/**
 * Port sortant de lecture : listes paginées d'interventions, supprimées logiquement exclues, de la plus récemment créée à la
 * plus ancienne (contrat observé, KB-GMAO-54). Le filtre de statut d'un centre est le code exact (casse comprise) ; vide ou
 * nul : tous. {@code page} et {@code size} sont déjà validés.
 */
public interface LectureInterventions {

    /**
     * Une intervention à consulter (suivi, bon) : une intervention supprimée logiquement est absente, comme pour un
     * identifiant inconnu (KB-GMAO-69, corrigé : le legacy la servait). Le filtre de centre est celui des cas d'usage.
     */
    Optional<Intervention> parId(InterventionId id);

    /** Dernière intervention d'un équipement par date de début, supprimées exclues, sans filtre de centre (KB-GMAO-71). */
    Optional<Intervention> derniereDeLEquipement(EquipementId equipement);

    PageDInterventions<Intervention> duCentre(CentreId centre, String statut, int page, int size);

    PageDInterventions<Intervention> deLEquipement(EquipementId equipement, int page, int size);
}
