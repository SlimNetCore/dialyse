package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Presence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Rassemble ce que la présence lit : le planning des places (port publié du legacy, traduit dans le langage du contexte) et le
 * personnel du centre. Lecture seule, non mise en cache (absences et remplacements changent en cours de journée).
 */
final class LecturesDeLaPresence {
    private final PersonnelDeLaPresence personnel;
    private final PlanningDesPlaces places;

    LecturesDeLaPresence(PersonnelDeLaPresence personnel, PlanningDesPlaces places) {
        this.personnel = personnel;
        this.places = places;
    }

    DonneesDePresence charger(CentreId centre, LocalDate du, LocalDate au) {
        PlanningDesPlaces.Planning lu = places.sur(centre.valeur(), du, au);
        Set<UUID> isolement = new HashSet<>();
        lu.salles().stream().filter(PlanningDesPlaces.Salle::isolement).forEach(s -> isolement.add(s.id()));
        Presence.PlanningDesPlaces planning = new Presence.PlanningDesPlaces(
                lu.salles().stream().map(s -> new Presence.Salle(s.id(), s.nom())).toList(),
                lu.creneaux().stream().map(c -> new Presence.Creneau(c.id(), c.libelle(), c.ordre())).toList(),
                lu.occupations().stream().map(o -> new Presence.Occupation(o.salle(), o.creneau(), jours(o.jours()), o.premierJour(),
                        o.dernierJour())).toList(),
                jours(lu.joursOuverts()), isolement,
                lu.fermetures().stream().map(f -> new Presence.Fermeture(f.date(), f.motif())).toList());
        Personnel gens = personnel.lire(centre, du, au);
        return new DonneesDePresence(planning, gens);
    }

    private static Set<JourDeLaSemaine> jours(Set<DayOfWeek> civils) {
        Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
        if (civils != null) civils.forEach(j -> jours.add(JourDeLaSemaine.de(j)));
        return jours;
    }
}
