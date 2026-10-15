package com.hemodialyse.backend.contexts.comptabilite.application.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;

/**
 * Le mapping effectif d'un centre : celui qu'il a enregistré, sinon le mapping par défaut (rien n'est enregistré par la lecture).
 */
public final class MappingDuCentreVu {

    private final MappingComptableRepository mappings;

    public MappingDuCentreVu(MappingComptableRepository mappings) {
        this.mappings = mappings;
    }

    public MappingComptable effectif(CentreId centre) {
        return mappings.duCentre(centre).orElseGet(() -> MappingComptable.parDefaut(centre));
    }
}
