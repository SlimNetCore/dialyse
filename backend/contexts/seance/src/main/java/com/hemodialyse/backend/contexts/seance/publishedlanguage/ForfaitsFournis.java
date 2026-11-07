package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0088) : le forfait courant d'une séance. Le forfait imposé sur la séance
 * prime ; sinon celui de la prise en charge qui couvre le jour (effective d'abord), sinon la plus récente qui en porte un. Aucun : vide.
 */
public interface ForfaitsFournis {

    Optional<Forfait> courant(UUID centre, UUID seance, UUID patient, LocalDate jour);

    record Forfait(UUID id, String code, String nom, BigDecimal prix, Integer nombreDeSeances, OffsetDateTime misAJourLe,
                   String misAJourPar, boolean surcharge) {
    }
}
