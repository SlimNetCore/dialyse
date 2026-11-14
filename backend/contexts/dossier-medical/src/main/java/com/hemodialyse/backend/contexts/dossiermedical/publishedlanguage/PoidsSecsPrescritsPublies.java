package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (LEC-PLN-GMAO c, ADR 0180) : les poids secs cibles prescrits à un patient d'un centre, pour que la
 * Séance (qui ne peut pas dépendre du Dossier médical) retienne celui en vigueur le jour de chaque séance.
 * Seules les prescriptions PORTANT un poids sec sont rendues, de la plus récente à la plus ancienne : date de prescription décroissante,
 * puis mise à jour décroissante (mise à jour inconnue en dernier), puis identifiant décroissant. Ce départage est celui, déterministe, de
 * l'ancienne sous-requête de la Séance (L8) ; il diffère volontairement de {@link StatistiquesMedicalesPubliees#poidsSecCibleEnVigueur}.
 */
public interface PoidsSecsPrescritsPublies {

    /** Poids secs cibles prescrits du patient au centre, du plus récent au plus ancien ; liste vide si aucun. */
    List<PoidsSecPrescrit> poidsSecsCiblesPrescrits(UUID centre, UUID patient);

    /** Un poids sec cible (kg) prescrit à la date donnée. */
    record PoidsSecPrescrit(LocalDate datePrescription, BigDecimal poidsSecCibleKg) {
    }
}
