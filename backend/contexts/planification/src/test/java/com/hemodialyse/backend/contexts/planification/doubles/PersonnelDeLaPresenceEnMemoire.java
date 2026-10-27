package com.hemodialyse.backend.contexts.planification.doubles;

import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.Presence.RemplacementPose;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Double en mémoire du personnel de la présence et des centres actifs : même contrat que les adaptateurs JDBC (infirmiers actifs triés
 * par nom puis prénom, roulement des actifs seulement, absences qui touchent la période, remplacements de la période, ratio lu avec
 * repli sur le défaut).
 */
public class PersonnelDeLaPresenceEnMemoire implements PersonnelDeLaPresence, CentresActifs {
    private record Fiche(UUID id, CentreId centre, String nom, String prenom, boolean actif, boolean habilite) {
    }

    private record Roulement(CentreId centre, UUID infirmier, UUID salle, UUID creneau, Set<DayOfWeek> jours) {
    }

    private record Absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
    }

    private record Remplacement(CentreId centre, UUID infirmier, LocalDate date, UUID salle, UUID creneau) {
    }

    private final List<Fiche> fiches = new ArrayList<>();
    private final List<Roulement> roulements = new ArrayList<>();
    private final List<Absence> absences = new ArrayList<>();
    private final List<Remplacement> remplacements = new ArrayList<>();
    private final Map<CentreId, Integer> ratios = new LinkedHashMap<>();
    private final Map<CentreId, Boolean> centres = new LinkedHashMap<>();

    public void centre(CentreId centre, boolean actif) {
        centres.put(centre, actif);
    }

    public void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif, boolean habilite) {
        fiches.add(new Fiche(id, centre, nom, prenom, actif, habilite));
    }

    public void affectation(CentreId centre, UUID infirmier, UUID salle, UUID creneau, Set<DayOfWeek> jours) {
        roulements.add(new Roulement(centre, infirmier, salle, creneau, jours));
    }

    public void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
        absences.add(new Absence(centre, infirmier, debut, fin));
    }

    public void remplacement(CentreId centre, UUID infirmier, LocalDate date, UUID salle, UUID creneau) {
        remplacements.add(new Remplacement(centre, infirmier, date, salle, creneau));
    }

    public void ratio(CentreId centre, Integer valeur) {
        ratios.put(centre, valeur);
    }

    @Override
    public Personnel lire(CentreId centre, LocalDate du, LocalDate au) {
        List<Fiche> actives = fiches.stream().filter(f -> f.centre().equals(centre) && f.actif())
                .sorted(Comparator.comparing(Fiche::nom).thenComparing(f -> f.prenom() == null ? "" : f.prenom()).thenComparing(Fiche::id))
                .toList();
        Set<UUID> ids = new java.util.HashSet<>();
        actives.forEach(f -> ids.add(f.id()));
        return new Personnel(
                actives.stream().map(f -> new InfirmierDeLaPresence(f.id(), f.nom(), f.prenom(), Qualification.INFIRMIER, f.habilite()))
                        .toList(),
                roulements.stream().filter(r -> r.centre().equals(centre) && ids.contains(r.infirmier()))
                        .map(r -> new AffectationPrevue(r.infirmier(), r.salle(), r.creneau(), jours(r.jours()))).toList(),
                absences.stream().filter(a -> a.centre().equals(centre) && !a.debut().isAfter(au) && !a.fin().isBefore(du))
                        .map(a -> new AbsenceConnue(a.infirmier(), a.debut(), a.fin(), TypeAbsence.CONGE)).toList(),
                remplacements.stream().filter(r -> r.centre().equals(centre) && !r.date().isBefore(du) && !r.date().isAfter(au))
                        .map(r -> new RemplacementPose(UUID.randomUUID(), r.date(), r.salle(), r.creneau(), r.infirmier())).toList(),
                RatioDeLaPresence.depuisLaBase(ratios.get(centre)));
    }

    @Override
    public List<CentreId> liste() {
        return centres.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
                .sorted(Comparator.comparing(c -> c.valeur().toString())).toList();
    }

    private static Set<JourDeLaSemaine> jours(Set<DayOfWeek> civils) {
        Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
        civils.forEach(j -> jours.add(JourDeLaSemaine.de(j)));
        return jours;
    }
}
