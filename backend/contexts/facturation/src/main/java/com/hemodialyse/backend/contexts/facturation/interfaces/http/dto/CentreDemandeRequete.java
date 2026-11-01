package com.hemodialyse.backend.contexts.facturation.interfaces.http.dto;

import java.util.UUID;

/**
 * Corps facultatif de la clôture : seul le centre (par défaut, celui de la session).
 */
public record CentreDemandeRequete(UUID centerId) {
}
