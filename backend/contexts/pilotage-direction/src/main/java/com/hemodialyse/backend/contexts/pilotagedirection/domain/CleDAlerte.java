package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

/**
 * L'identité d'une alerte OUVERTE : le centre et le code, sans notion de jour (R2 option (a), KB-DIR-04 : tant qu'une alerte du même centre et
 * du même code est ouverte, aucune autre n'est ouverte ; une alerte qui réapparaît après sa résolution est une NOUVELLE ligne).
 */
public record CleDAlerte(UUID centreId, String code) {
}
