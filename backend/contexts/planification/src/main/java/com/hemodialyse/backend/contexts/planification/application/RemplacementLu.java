package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Remplacement;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un remplacement tel qu'affiché (read model).
 */
public record RemplacementLu(UUID id, LocalDate date, UUID salle, UUID creneau, UUID infirmier, UUID remplace) {

    static RemplacementLu depuis(Remplacement r) {
        return new RemplacementLu(r.id().valeur(), r.date(), r.salle().valeur(), r.creneau().valeur(), r.remplacant().valeur(),
                r.remplace().map(i -> i.valeur()).orElse(null));
    }
}
