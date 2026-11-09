package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port FOURNI au Pilotage direction par la plateforme ({@code pilotagedirection.v1}, ADR 0103, PD2) : les indicateurs d'une société sur un
 * mois, tels que {@code /indicators} les sert, À FIGER dans l'instantané mensuel.
 * <p>
 * La valeur est OPAQUE pour le Pilotage : un objet sérialisable en JSON que l'instantané grave tel quel (RG-DIR-090, il n'est jamais
 * interprété ni recalculé ensuite). C'est ce qui garde le JSON figé identique à celui des instantanés existants sans que le Pilotage
 * possède le modèle des indicateurs (restés en plateforme depuis DM9, ADR 0101).
 */
public interface IndicateursAFigerFournis {

    Object pour(UUID societeId, LocalDate debut, LocalDate fin);
}
