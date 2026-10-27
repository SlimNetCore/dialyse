package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053, slice P4), fourni par le legacy (planning des places et calendrier du centre, slice P5
 * et Organisation) : ce dont la présence a besoin pour compter les patients par case et savoir quels jours le centre dialyse. Lecture
 * seule, bornée au centre ; retiré quand les places et le calendrier auront leur contexte.
 */
public interface PlanningDesPlaces {

    record Salle(UUID id, String nom, boolean isolement) {
    }

    record Creneau(UUID id, String libelle, int ordre) {
    }

    /** Place occupée par un patient actif, aux jours indiqués ; premier et dernier jours d'occupation facultatifs (nul = sans limite). */
    record Occupation(UUID salle, UUID creneau, Set<DayOfWeek> jours, LocalDate premierJour, LocalDate dernierJour) {
    }

    record Fermeture(LocalDate date, String motif) {
    }

    /**
     * @param joursOuverts jours d'ouverture hebdomadaires ; vide : tous
     * @param fermetures   fériés et fermetures exceptionnelles de la période
     */
    record Planning(List<Salle> salles, List<Creneau> creneaux, List<Occupation> occupations, Set<DayOfWeek> joursOuverts,
                    List<Fermeture> fermetures) {
    }

    /** Le planning des places du centre sur la période (bornes incluses). */
    Planning sur(UUID centre, LocalDate du, LocalDate au);
}
