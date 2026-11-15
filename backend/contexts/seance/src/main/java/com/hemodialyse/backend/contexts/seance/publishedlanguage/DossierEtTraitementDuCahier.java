package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par le Dossier médical, DOC5a, ADR 0193) : ce que le cahier de dialyse imprime du dossier médical du
 * patient et du traitement en vigueur le jour d'une séance. Le Dossier médical dépend de la Séance : la Séance ne peut pas le lire
 * directement ; elle compose son jeu de données du cahier avec ces valeurs BRUTES (elle les met en forme).
 */
public interface DossierEtTraitementDuCahier {

    /** Le dossier médical de base du patient du centre ; vide s'il n'en a pas. */
    Optional<DossierDeBase> dossier(UUID centre, UUID patient);

    /**
     * La prescription en vigueur le jour donné : la plus récente datée au plus tard ce jour (date décroissante, puis création décroissante,
     * puis identifiant décroissant), qu'elle porte ou non un poids sec ou un traitement ; vide si aucune.
     */
    Optional<TraitementEnVigueur> traitementEnVigueur(UUID centre, UUID patient, LocalDate jour);

    /** Valeurs brutes du dossier (statuts d'hépatite : codes du dossier, texte libre toléré). */
    record DossierDeBase(String nephropathieInitiale, LocalDate dateMiseEnDialyse, String statutHepatiteB, String statutHepatiteC,
                         String observationGlobale, String conclusionMedicale) {
    }

    /** Le poids sec cible et les deux traitements de la prescription ; un traitement sans article ni dose vaut un traitement vide. */
    record TraitementEnVigueur(BigDecimal poidsSecCibleKg, Traitement epo, Traitement fer) {
    }

    /**
     * @param article           libellé de l'article du centre, à défaut son code ; nul si aucun article ou article inconnu du centre
     * @param dose              UI pour l'EPO, mg pour le fer ; nulle si non prescrite
     * @param frequenceUnite    code de l'unité (HEURE, JOUR, SEMAINE, MOIS, ANNEE) ou texte tel que saisi
     */
    record Traitement(String article, Integer dose, String voie, Integer frequenceValeur, String frequenceUnite) {
    }
}
