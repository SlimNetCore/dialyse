package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Les seize mesures facultatives d'un bilan à colonnes fixes (NFS, bilan martial, adéquation de dialyse, phospho-calcique, nutrition,
 * inflammation). Aucune plausibilité : négative, nulle ou démesurée, toute valeur est acceptée (KB-MED-79 reproduit, à confirmer avec les
 * métiers) ; aucune interprétation normal / anormal (elle vit chez les lecteurs : anémie, KDIGO). Bornes de colonne (400 au lieu du 500 du
 * legacy, KB-MED-72) : {@code numeric(38,2)}, soit 36 chiffres entiers après arrondi à 2 décimales ; les décimales au-delà de 2 sont
 * arrondies par la base (11,555 devient 11,56).
 * <p>
 * Identifiants de contrat (JSON, colonnes) : hémoglobine {@code hbGDl}, hématocrite {@code htPct}, coefficient de saturation
 * {@code cstfPct}, urée avant / après séance {@code ureePreMgDl} / {@code ureePostMgDl}, Kt/V mensuel {@code ktVMensuel}, CRP {@code crpMgL}.
 */
public record MesuresDuBilan(BigDecimal hemoglobine, BigDecimal hematocrite, Integer plaquettes, BigDecimal ferritine,
                             BigDecimal coefficientDeSaturation, BigDecimal epoEndogene, BigDecimal ureeAvantSeance,
                             BigDecimal ureeApresSeance, BigDecimal creatinine, BigDecimal ktVMensuel, BigDecimal phosphore,
                             BigDecimal calcium, BigDecimal pth, BigDecimal albumine, BigDecimal proteines, BigDecimal crp) {
    private static final int CHIFFRES_MAXIMAUX = 38;
    private static final int DECIMALES = 2;

    /** Refuse (400) une mesure qui ne tient pas dans sa colonne. */
    void verifierLesBornes() {
        for (BigDecimal mesure : new BigDecimal[]{hemoglobine, hematocrite, ferritine, coefficientDeSaturation, epoEndogene, ureeAvantSeance,
                ureeApresSeance, creatinine, ktVMensuel, phosphore, calcium, pth, albumine, proteines, crp}) {
            if (mesure == null) continue;
            if (mesure.precision() - mesure.scale() > CHIFFRES_MAXIMAUX - DECIMALES + 1
                    || mesure.setScale(DECIMALES, RoundingMode.HALF_UP).precision() > CHIFFRES_MAXIMAUX) {
                throw new SaisieInvalide("Une mesure dépasse 36 chiffres entiers et 2 décimales");
            }
        }
    }
}
