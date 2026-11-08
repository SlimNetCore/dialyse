package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0092) : un forfait du référentiel d'un centre (table {@code forfait}), lu pour
 * en imposer un à une séance. Un forfait inconnu ou d'un autre centre : vide. Le prix peut être absent (KB-SEA-41).
 */
public interface ReferentielDeForfaitsFourni {

    Optional<Forfait> forfait(UUID centre, UUID forfait);

    record Forfait(UUID id, String code, String libelle, BigDecimal prix) {
    }
}
