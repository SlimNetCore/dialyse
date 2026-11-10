package com.hemodialyse.backend.contexts.seance.domain;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : la chaîne des payeurs d'un centre (propriété de Patient et couverture, RF3b, ADR 0127), telle que la Séance la lit pour
 * nommer la caisse d'un centre payeur et pour connaître l'agence d'un centre payeur. Toujours bornée au centre indiqué.
 */
public interface ChaineDesPayeursDuCentre {

    /** La caisse de chaque centre payeur du centre ; un centre payeur sans caisse DANS CE CENTRE est absent de la table. */
    Map<UUID, CaisseDuPayeur> caissesDesCentresPayeurs(UUID centre);

    /** L'agence enregistrée sur ce centre payeur ; vide si le centre payeur est inconnu de ce centre. */
    Optional<UUID> agenceDuCentrePayeur(UUID centre, UUID centrePayeur);

    /** Code et nom d'une caisse d'assurance. */
    record CaisseDuPayeur(String code, String nom) {
    }
}
