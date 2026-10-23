package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fiche d'un article (RG-STK-001, 006, 008) : identité, unités, achat, conservation et sécurité, comptes comptables. Valeur
 * immuable, validée à la saisie dans l'ordre du legacy ({@code Article.appliquerFiche}) et nettoyée (espaces de bord retirés,
 * champs facultatifs blancs ramenés à « absent ») ; le stock et le PMP n'en font pas partie (ils résultent des mouvements).
 */
public record FicheArticle(String code, String libelle, String dci, String formeGalenique, String codeBarres,
                           String referenceFabricant, String unite, String uniteAchat, BigDecimal coefficientAchat,
                           BigDecimal dosageParUnite, String uniteDosage, UUID fournisseurId, UUID tvaTypeId,
                           BigDecimal prixAchat, BigDecimal seuilAlerte, BigDecimal stockMax, boolean gereParLot,
                           boolean peremptionObligatoire, ConditionConservation conditionConservation,
                           boolean produitDangereux, boolean dechetDasri, TypeTraitementAnemie typeTraitementAnemie,
                           String compteStock, String compteCharge) {

    private static final Pattern COMPTE = Pattern.compile("[0-9A-Za-z]{1,20}");

    private static boolean blanc(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private static boolean negatif(BigDecimal valeur) {
        return valeur != null && valeur.signum() < 0;
    }

    private static String nettoye(String valeur) {
        return blanc(valeur) ? null : valeur.trim();
    }

    private static boolean compteInvalide(String compte) {
        return !blanc(compte) && !COMPTE.matcher(compte.trim()).matches();
    }

    /**
     * La fiche saisie, validée puis nettoyée ; le seuil d'alerte absent vaut 0.
     *
     * @throws RefusDuReferentiel au premier invariant violé, avec le code du legacy
     */
    public static FicheArticle saisie(String code, String libelle, String dci, String formeGalenique, String codeBarres,
                                      String referenceFabricant, String unite, String uniteAchat, BigDecimal coefficientAchat,
                                      BigDecimal dosageParUnite, String uniteDosage, UUID fournisseurId, UUID tvaTypeId,
                                      BigDecimal prixAchat, BigDecimal seuilAlerte, BigDecimal stockMax, boolean gereParLot,
                                      boolean peremptionObligatoire, ConditionConservation conditionConservation,
                                      boolean produitDangereux, boolean dechetDasri,
                                      TypeTraitementAnemie typeTraitementAnemie, String compteStock, String compteCharge) {
        if (blanc(code)) {
            throw new RefusDuReferentiel("ARTICLE_CODE_REQUIS", "Le code article est obligatoire");
        }
        if (blanc(libelle)) {
            throw new RefusDuReferentiel("ARTICLE_LIBELLE_REQUIS", "Le libellé article est obligatoire");
        }
        if (blanc(unite)) {
            throw new RefusDuReferentiel("ARTICLE_UNITE_REQUISE", "L'unité de stock est obligatoire");
        }
        boolean dosage = dosageParUnite != null;
        if (dosage == blanc(uniteDosage)) {
            throw new RefusDuReferentiel("ARTICLE_DOSAGE_INCOMPLET", "Le dosage par unité et son unité (UI, mg…) vont ensemble");
        }
        if (dosage && dosageParUnite.signum() <= 0) {
            throw new RefusDuReferentiel("ARTICLE_DOSAGE_INVALIDE", "Le dosage par unité doit être strictement positif");
        }
        if (coefficientAchat != null && coefficientAchat.signum() <= 0) {
            throw new RefusDuReferentiel("ARTICLE_COEFFICIENT_ACHAT_INVALIDE", "Le coefficient d'achat doit être strictement positif");
        }
        if (negatif(prixAchat) || negatif(seuilAlerte) || negatif(stockMax)) {
            throw new RefusDuReferentiel("ARTICLE_VALEUR_NEGATIVE", "Prix, seuil et stock maximum ne peuvent être négatifs");
        }
        BigDecimal seuil = seuilAlerte != null ? seuilAlerte : BigDecimal.ZERO;
        if (stockMax != null && stockMax.compareTo(seuil) < 0) {
            throw new RefusDuReferentiel("ARTICLE_STOCK_MAX_INFERIEUR_SEUIL", "Le stock maximum ne peut être inférieur au seuil d'alerte");
        }
        if (compteInvalide(compteStock) || compteInvalide(compteCharge)) {
            throw new RefusDuReferentiel("ARTICLE_COMPTE_INVALIDE", "Un compte comptable comporte 1 à 20 lettres ou chiffres");
        }
        return new FicheArticle(code.trim(), libelle.trim(), nettoye(dci), nettoye(formeGalenique), nettoye(codeBarres),
                nettoye(referenceFabricant), unite.trim(), nettoye(uniteAchat), coefficientAchat, dosageParUnite,
                nettoye(uniteDosage), fournisseurId, tvaTypeId, prixAchat, seuil, stockMax, gereParLot, peremptionObligatoire,
                conditionConservation, produitDangereux, dechetDasri, typeTraitementAnemie, nettoye(compteStock),
                nettoye(compteCharge));
    }

    /**
     * La conversion de dose de cet article (RG-STK-006).
     */
    public Dosage dosage() {
        return new Dosage(dosageParUnite, uniteDosage, unite, code);
    }
}

