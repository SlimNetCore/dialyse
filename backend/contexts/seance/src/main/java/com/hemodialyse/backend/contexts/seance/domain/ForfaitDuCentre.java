package com.hemodialyse.backend.contexts.seance.domain;

import java.math.BigDecimal;

/**
 * Un forfait du référentiel du centre, tel que la Séance le copie quand elle en impose un à une séance. Le prix peut être absent ou
 * nul (KB-SEA-41, reproduit : la colonne du référentiel est facultative) ; il est recopié tel quel, jamais recalculé, ni arrondi.
 */
public record ForfaitDuCentre(ForfaitId id, String code, String nom, BigDecimal prix) {
    public ForfaitDuCentre {
        if (id == null) throw new SaisieInvalide("Forfait requis");
    }
}
