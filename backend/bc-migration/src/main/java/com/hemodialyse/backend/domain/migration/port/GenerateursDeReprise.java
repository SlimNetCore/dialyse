package com.hemodialyse.backend.domain.migration.port;

import java.util.List;
import java.util.UUID;

/**
 * Port Out — générateurs de dialyse d'un centre, pour retrouver celui d'un patient repris (par son code). Tous statuts :
 * un générateur hors service, en maintenance ou réformé est accepté tel quel (KB-GMAO-81, reproduit). Supprimés exclus.
 */
public interface GenerateursDeReprise {

    List<GenerateurRepris> duCentre(UUID centreId);

    record GenerateurRepris(UUID id, String code, UUID salleId) {
    }
}
