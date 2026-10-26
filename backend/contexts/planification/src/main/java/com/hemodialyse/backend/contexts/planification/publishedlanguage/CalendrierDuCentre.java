package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053), fourni par le legacy (calendrier du centre : jours d'ouverture hebdomadaires, jours
 * fériés, fermetures exceptionnelles, encore dans la présence P4 et Organisation). Retiré quand le calendrier aura son contexte.
 */
public interface CalendrierDuCentre {

    /**
     * Le centre dialyse-t-il ce jour-là ?
     */
    boolean ouvert(UUID centre, LocalDate jour);
}
