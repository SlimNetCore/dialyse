package com.hemodialyse.backend.contexts.comptabilite.application.mapping;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

/**
 * Le mapping comptable du centre ; sans mapping enregistré, les valeurs par défaut, sans rien enregistrer.
 */
public final class ConsulterLeMapping {

    private final MappingDuCentreVu mappings;
    private final UniteDeTravail uniteDeTravail;

    public ConsulterLeMapping(MappingDuCentreVu mappings, UniteDeTravail uniteDeTravail) {
        this.mappings = mappings;
        this.uniteDeTravail = uniteDeTravail;
    }

    public MappingVue executer(CentreId centre) {
        return uniteDeTravail.executer(() -> MappingVue.depuis(mappings.effectif(centre)));
    }
}
