package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ce que dit une prescription médicale : cibles de séance de dialyse (débit sang, débit dialysat, ultrafiltration maximale,
 * durée, poids sec, dialyseur, anticoagulant) et traitements de l'anémie (EPO, fer). Valeur immuable, validée à la construction.
 * <p>
 * Reproduit (KB-MED-30, à confirmer avec les métiers) : seul le poids sec est borné (20 à 300 kg, arrondi au centième, hors
 * borne 400 {@code VALIDATION_ERROR}) ; débits, durée, ultrafiltration et doses acceptent zéro et négatifs ; date future
 * acceptée. Écart décidé (KB-MED-22) : un texte plus long que sa colonne est refusé en 400 (avant : 500).
 */
public record ContenuDePrescription(LocalDate date, UUID medecin, Integer qbCible, Integer qdCible, Integer ufMaxMl,
                                    Integer dureeCibleMin, BigDecimal poidsSecCibleKg, String typeDialyseur, String anticoagulant,
                                    Traitement epo, Traitement fer) {

    public static final BigDecimal POIDS_SEC_MIN_KG = new BigDecimal("20.00");
    public static final BigDecimal POIDS_SEC_MAX_KG = new BigDecimal("300.00");

    public ContenuDePrescription {
        if (poidsSecCibleKg != null) {
            if (poidsSecCibleKg.compareTo(POIDS_SEC_MIN_KG) < 0) throw new ValeurHorsBorne("Le poids sec cible doit être >= 20 kg");
            if (poidsSecCibleKg.compareTo(POIDS_SEC_MAX_KG) > 0) throw new ValeurHorsBorne("Le poids sec cible doit être <= 300 kg");
            poidsSecCibleKg = poidsSecCibleKg.setScale(2, RoundingMode.HALF_UP);
        }
        if (typeDialyseur != null && typeDialyseur.length() > 100) throw new SaisieInvalide("Le type de dialyseur est limité à 100 caractères");
        if (anticoagulant != null && anticoagulant.length() > 20) throw new SaisieInvalide("L'anticoagulant est limité à 20 caractères");
        epo = epo == null ? Traitement.AUCUN : epo;
        fer = fer == null ? Traitement.AUCUN : fer;
    }

    /** Même contenu à une autre date (date par défaut = jour du centre, posée par le cas d'usage). */
    public ContenuDePrescription aLaDate(LocalDate nouvelleDate) {
        return new ContenuDePrescription(nouvelleDate, medecin, qbCible, qdCible, ufMaxMl, dureeCibleMin, poidsSecCibleKg,
                typeDialyseur, anticoagulant, epo, fer);
    }
}
