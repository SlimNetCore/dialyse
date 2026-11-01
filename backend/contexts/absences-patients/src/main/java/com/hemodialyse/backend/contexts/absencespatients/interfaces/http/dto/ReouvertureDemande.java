package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

/**
 * Corps de la réouverture : le motif est obligatoire (vérifié par le domaine).
 */
public record ReouvertureDemande(String motif) {
}
