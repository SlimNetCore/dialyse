package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SurEffectifDuRoulement;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fournit au roulement (P3) l'avertissement de sur-effectif d'une affectation (RG-INF-047), jamais bloquant : calculé par la présence du
 * contexte, plus par le legacy. Jours de la semaine de {@code aujourdhui} où la case compte plus d'infirmiers que l'effectif requis.
 */
public class SurEffectifCalculeParLaPresence implements SurEffectifDuRoulement {
    private final LecturesDeLaPresence lectures;

    public SurEffectifCalculeParLaPresence(PersonnelDeLaPresence personnel, PlanningDesPlaces places) {
        this.lectures = new LecturesDeLaPresence(personnel, places);
    }

    @Override
    public List<String> joursEnSureffectif(UUID centre, UUID salle, UUID creneau, Set<String> jours, LocalDate aujourdhui) {
        LocalDate debut = PresenceDeLaSemaine.debutDeSemaine(aujourdhui);
        var semaine = PresenceDeLaSemaine.construire(lectures.charger(new CentreId(centre), debut, debut.plusDays(6)), debut);
        return semaine.cases().stream()
                .filter(c -> c.salleId().equals(salle) && c.creneauId().equals(creneau) && c.surplus() > 0)
                .map(CasePresence::jour).filter(j -> jours.contains(j.name())).sorted().map(JourDeLaSemaine::name).toList();
    }
}
