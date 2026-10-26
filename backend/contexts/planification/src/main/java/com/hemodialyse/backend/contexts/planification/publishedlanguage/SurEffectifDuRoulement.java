package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053), fourni par le legacy (présence de la semaine, slice P4) : l'avertissement de
 * sur-effectif d'une affectation (RG-INF-047), jamais bloquant.
 */
public interface SurEffectifDuRoulement {

    /**
     * Jours, parmi {@code jours} (noms {@code DIMANCHE} à {@code SAMEDI}), de la semaine de {@code aujourdhui} où la case (salle,
     * créneau) compte plus d'infirmiers que l'effectif requis, dans l'ordre de la semaine.
     */
    List<String> joursEnSureffectif(UUID centre, UUID salle, UUID creneau, Set<String> jours, LocalDate aujourdhui);
}
