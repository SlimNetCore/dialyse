package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Article tel que le Stock le publie aux autres contextes (Séance, Dossier médical) : identité, unités, dosage, état actif et
 * comptes comptables ; ni stock ni PMP (OHS, ADR 0006).
 */
public record ArticleDuCatalogue(UUID id, UUID centre, String code, String libelle, String unite, boolean actif,
                                 String typeTraitementAnemie, BigDecimal dosageParUnite, String uniteDosage,
                                 boolean gereParLot, String compteStock, String compteCharge) {
}

