package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant : le calendrier du centre (jours fériés et fermetures exceptionnelles, tables {@code center_holiday} et
 * {@code center_closure_day}). Toutes les opérations sont bornées au centre demandé.
 */
public interface CalendrierDuCentre {

    /** Les jours de cette nature dans le mois (bornes incluses), du plus ancien au plus récent. */
    List<JourDuCalendrier> duMois(CentreId centre, NatureDeJour nature, MoisDuCalendrier mois);

    /**
     * Ajoute un jour. {@link JourDejaAuCalendrier} si le centre a déjà un jour de cette nature à cette date (l'unicité est tenue par la
     * base : deux ajouts simultanés ne passent jamais tous les deux).
     */
    void ajouter(JourDuCalendrier jour);

    /**
     * Retire le jour de cette nature, de ce centre. Rend faux, sans erreur, si rien n'a été retiré (identifiant inconnu, d'une autre
     * nature ou d'un autre centre : KB-SEA-58, reproduit).
     */
    boolean retirer(CentreId centre, NatureDeJour nature, UUID id);
}
