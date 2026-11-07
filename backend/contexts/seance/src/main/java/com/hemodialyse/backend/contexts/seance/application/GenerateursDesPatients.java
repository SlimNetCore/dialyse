package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : le générateur du patient, équipement de la GMAO (source de vérité unique). Inconnu, supprimé ou d'un autre centre : vide. */
public interface GenerateursDesPatients {
    Optional<Generateur> duCentre(CentreId centre, UUID equipement);

    record Generateur(UUID id, String code, String fabricant, String statut) {
    }
}
