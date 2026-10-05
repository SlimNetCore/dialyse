package com.hemodialyse.backend.domain.article.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Données éditables de la fiche article (création et modification). Le stock, le PMP et l'état actif ne sont jamais
 * saisis ici : ils résultent des mouvements de stock.
 *
 * @param unite            unité de stock ET de sortie (administration, consommation de séance)
 * @param uniteAchat       unité dans laquelle on commande (ex. boîte) ; vide = identique à l'unité de stock
 * @param coefficientAchat nombre d'unités de stock dans une unité d'achat (ex. boîte de 10 seringues = 10)
 * @param dosageParUnite   quantité de principe actif contenue dans une unité de stock (ex. 4000 pour 1 seringue)
 * @param uniteDosage      unité de ce dosage (ex. UI, mg) : celle de la prescription du médecin
 */
public record ArticleFiche(
        String code,
        String libelle,
        String dci,
        String formeGalenique,
        String codeBarres,
        String referenceFabricant,
        String unite,
        String uniteAchat,
        BigDecimal coefficientAchat,
        BigDecimal dosageParUnite,
        String uniteDosage,
        UUID fournisseurId,
        UUID tvaTypeId,
        BigDecimal prixAchat,
        BigDecimal seuilAlerte,
        BigDecimal stockMax,
        boolean gereParLot,
        boolean peremptionObligatoire,
        ConditionConservation conditionConservation,
        boolean produitDangereux,
        boolean dechetDasri,
        TypeTraitementAnemie typeTraitementAnemie
) {
}
