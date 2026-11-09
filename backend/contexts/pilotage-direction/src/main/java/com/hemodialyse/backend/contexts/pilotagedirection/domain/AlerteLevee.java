package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une alerte actuellement levée pour un centre. {@code valeur} est nulle quand l'alerte n'en porte pas (observance en retard). Le nom du
 * centre et la valeur sont ceux du constat ; l'historique ne retient que ceux de la première apparition.
 */
public record AlerteLevee(UUID centreId, String centre, String code, GraviteDAlerte gravite, BigDecimal valeur) {

    public CleDAlerte cle() {
        return new CleDAlerte(centreId, code);
    }
}
