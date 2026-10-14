package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie;

import java.time.Instant;
import java.util.UUID;

/** Un événement de la chronologie d'une intervention, avec le nom de son auteur (absent si l'auteur est inconnu). */
public record EvenementVu(EvenementDeChronologie.Type type, Instant le, UUID par, String parNom, String detail) {
}
