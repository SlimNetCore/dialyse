package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import java.util.UUID;

/**
 * Corps du transfert d'un centre (route canonique `/centres/{id}/transferer`).
 */
public record TransfererCentreRequete(UUID nouvelleSocieteId) {
}
