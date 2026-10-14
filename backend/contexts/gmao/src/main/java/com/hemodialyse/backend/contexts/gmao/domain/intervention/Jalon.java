package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/**
 * Qui a fait quoi, et quand : création, démarrage, clôture, annulation ou dernière modification d'une intervention.
 * L'auteur peut manquer sur des lignes anciennes lues en base.
 */
public record Jalon(UtilisateurId par, Instant le) {
}
