package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.domain.referentiel.ConditionConservation;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corps de création et de modification d'une fiche article (même JSON que le legacy). Les quatre booléens absents valent
 * {@code false} (KB-STK-03 corrigé : le legacy répondait 500) ; une condition de conservation ou un type de traitement inconnu
 * est refusé avec un code stable (KB-STK-08 corrigé).
 */
public record FicheArticleRequete(
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
        Boolean gereParLot,
        Boolean peremptionObligatoire,
        String conditionConservation,
        Boolean produitDangereux,
        Boolean dechetDasri,
        String typeTraitementAnemie,
        @Size(max = 20) String compteStock,
        @Size(max = 20) String compteCharge) {

    private static boolean vrai(Boolean valeur) {
        return Boolean.TRUE.equals(valeur);
    }

    static ConditionConservation condition(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return ConditionConservation.valueOf(valeur);
        } catch (IllegalArgumentException e) {
            throw new ValeurInvalide("ARTICLE_CONDITION_CONSERVATION_INVALIDE",
                    "Condition de conservation inconnue : " + valeur + " (AMBIANT, REFRIGERE ou CONGELE)");
        }
    }

    static TypeTraitementAnemie typeDeTraitement(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return TypeTraitementAnemie.valueOf(valeur);
        } catch (IllegalArgumentException e) {
            throw new ValeurInvalide("ARTICLE_TYPE_TRAITEMENT_INVALIDE",
                    "Type de traitement de l'anémie inconnu : " + valeur + " (EPO ou FER_INJECTABLE)");
        }
    }

    FicheArticle fiche() {
        return FicheArticle.saisie(code, libelle, dci, formeGalenique, codeBarres, referenceFabricant, unite, uniteAchat,
                coefficientAchat, dosageParUnite, uniteDosage, fournisseurId, tvaTypeId, prixAchat, seuilAlerte, stockMax,
                vrai(gereParLot), vrai(peremptionObligatoire), condition(conditionConservation), vrai(produitDangereux),
                vrai(dechetDasri), typeDeTraitement(typeTraitementAnemie), compteStock, compteCharge);
    }
}

