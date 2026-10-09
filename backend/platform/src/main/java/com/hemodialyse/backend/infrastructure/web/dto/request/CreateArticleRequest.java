package com.hemodialyse.backend.infrastructure.web.dto.request;

import com.hemodialyse.backend.domain.article.model.ArticleFiche;
import com.hemodialyse.backend.domain.article.model.ConditionConservation;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fiche article (création et modification). Les règles métier (dosage complet, coefficients positifs…) sont
 * protégées par l'agrégat {@code Article} ; ici seule la forme de la requête est contrôlée.
 */
public record CreateArticleRequest(
        @NotNull UUID centerId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String libelle,
        @Size(max = 150) String dci,
        @Size(max = 80) String formeGalenique,
        @Size(max = 64) String codeBarres,
        @Size(max = 80) String referenceFabricant,
        @NotBlank @Size(max = 30) String unite,
        @Size(max = 30) String uniteAchat,
        BigDecimal coefficientAchat,
        BigDecimal dosageParUnite,
        @Size(max = 20) String uniteDosage,
        UUID fournisseurId,
        UUID tvaTypeId,
        BigDecimal prixAchat,
        BigDecimal seuilAlerte,
        BigDecimal stockMax,
        boolean gereParLot,
        boolean peremptionObligatoire,
        String conditionConservation,
        boolean produitDangereux,
        boolean dechetDasri,
        String typeTraitementAnemie,
        @Size(max = 20) String compteStock,
        @Size(max = 20) String compteCharge
) {

    public ArticleFiche toFiche() {
        return new ArticleFiche(code, libelle, dci, formeGalenique, codeBarres, referenceFabricant, unite, uniteAchat,
                coefficientAchat, dosageParUnite, uniteDosage, fournisseurId, tvaTypeId, prixAchat, seuilAlerte,
                stockMax, gereParLot, peremptionObligatoire,
                conditionConservation == null || conditionConservation.isBlank()
                        ? null : ConditionConservation.valueOf(conditionConservation),
                produitDangereux, dechetDasri,
                typeTraitementAnemie == null || typeTraitementAnemie.isBlank()
                        ? null : TypeTraitementAnemie.valueOf(typeTraitementAnemie),
                compteStock, compteCharge);
    }
}
