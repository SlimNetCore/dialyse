package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import java.util.UUID;

/** Identité d'un équipement ; les autres agrégats le référencent par cette identité seulement. */
public record EquipementId(UUID valeur) {
    public EquipementId {
        if (valeur == null) throw new IllegalArgumentException("Équipement requis");
    }
}
