package com.hemodialyse.backend.contexts.facturation.interfaces.http.dto;

import java.util.UUID;

/**
 * Corps de la réouverture : le motif est obligatoire (vérifié par le domaine).
 */
public record ReouvertureRequete(UUID centerId, String motif) {
}
