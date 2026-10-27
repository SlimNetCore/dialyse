package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du contexte GMAO : les périodes pendant lesquelles les générateurs de dialyse d'un centre sont immobilisés par une
 * intervention planifiée ou en cours, lues par l'optimisation de la Planification (maintenance des générateurs). Lecture seule.
 * <p>
 * Sont retenues les interventions non supprimées, planifiées ou en cours, d'un générateur de dialyse du centre, qui commencent au plus
 * tard le dernier jour de la période et se terminent au plus tôt le premier jour. Sans date de fin, une intervention planifiée immobilise
 * son seul jour de début et une intervention en cours tout le reste de la période. Les jours sont ceux du temps universel (UTC).
 */
public interface IndisponibilitesDesGenerateurs {

    List<Indisponibilite> entre(UUID centreId, LocalDate du, LocalDate au);

    /** @param type code du type d'intervention ; {@code fin} n'est jamais antérieure à {@code debut} */
    record Indisponibilite(UUID generateurId, LocalDate debut, LocalDate fin, String type) {
    }
}
