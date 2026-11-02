package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Presence.Absent;
import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Present;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Case;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * La semaine d'un infirmier dans la grille du centre, composée par le module lui-même (P9, ADR 0069) : la présence unique
 * ({@link ConsulterLaPresence}) et le planning réel ({@link ConsulterLaSemaine}), qui reposent sur le même calcul de présence
 * ({@code SejourDuPatient}). Un patient en sommeil n'est compté nulle part dans « Mon planning » : ni parmi les occupants, ni dans le
 * nombre de patients, ni dans l'effectif requis (écart décidé le 2026-11-02, KB-PLN-50 et KB-PLN-106). Aucune donnée
 * nominative d'un autre infirmier : les patients sont ceux des salles et créneaux de l'infirmier. Lecture seule.
 */
public class SemaineDeLInfirmierDuModule implements SemaineDeLInfirmier {

    private final ConsulterLaPresence presence;
    private final ConsulterLaSemaine planning;

    public SemaineDeLInfirmierDuModule(ConsulterLaPresence presence, ConsulterLaSemaine planning) {
        this.presence = presence;
        this.planning = planning;
    }

    @Override
    public PlanningDeLaSemaine semaine(UUID centre, UUID infirmier, LocalDate date) {
        CentreId id = new CentreId(centre);
        SemainePresence semaine = presence.semaine(id, date);
        List<CreneauPersonnel> mes = mesCreneaux(semaine, infirmier);
        SemaineDuPlanning.Semaine reel = planning.executer(id, date);
        return new PlanningDeLaSemaine(semaine.debut(), semaine.fin(),
                semaine.salles().stream().map(s -> new Salle(s.id(), s.nom())).toList(),
                semaine.creneaux().stream().map(c -> new Creneau(c.id(), c.libelle(), c.ordre())).toList(),
                mes,
                semaine.jours().stream().map(j -> new JourDuPlanning(j.jour().name(), j.date(), j.ouvertHebdomadaire(),
                        j.fermetureMotif())).toList(),
                mesCases(semaine, reel, mes, infirmier));
    }

    /** Une ligne par case où l'infirmier est prévu, remplaçant ou absent, triée par date puis par ordre du créneau. */
    static List<CreneauPersonnel> mesCreneaux(SemainePresence semaine, UUID infirmier) {
        Map<UUID, Integer> ordre = new HashMap<>();
        for (int i = 0; i < semaine.creneaux().size(); i++) ordre.put(semaine.creneaux().get(i).id(), i);
        List<CreneauPersonnel> creneaux = new ArrayList<>();
        for (CasePresence c : semaine.cases()) {
            for (Present p : c.presents()) {
                if (p.infirmierId().equals(infirmier)) {
                    creneaux.add(new CreneauPersonnel(c.date(), c.jour().name(), c.salleId(), c.creneauId(),
                            p.remplacant() ? "REMPLACANT" : "PREVU"));
                }
            }
            for (Absent a : c.absents()) {
                if (a.infirmierId().equals(infirmier)) {
                    creneaux.add(new CreneauPersonnel(c.date(), c.jour().name(), c.salleId(), c.creneauId(), "ABSENT"));
                }
            }
        }
        creneaux.sort(Comparator.comparing(CreneauPersonnel::date).thenComparing(c -> ordre.getOrDefault(c.creneauId(), 0)));
        return creneaux;
    }

    private static String cle(LocalDate date, UUID salle, UUID creneau) {
        return date + "|" + salle + "|" + creneau;
    }

    private static List<CaseDeLInfirmier> mesCases(SemainePresence semaine, SemaineDuPlanning.Semaine reel,
                                                   List<CreneauPersonnel> mes, UUID infirmier) {
        Set<String> miennes = mes.stream().map(c -> cle(c.date(), c.salleId(), c.creneauId())).collect(Collectors.toSet());
        Map<String, List<SemaineDuPlanning.Occupant>> occupants = reel.cases().stream().collect(Collectors.toMap(
                c -> cle(c), Case::occupants, (a, b) -> a));
        return semaine.cases().stream()
                .filter(c -> miennes.contains(cle(c.date(), c.salleId(), c.creneauId())))
                .map(c -> new CaseDeLInfirmier(c.date(), c.jour().name(), c.salleId(), c.creneauId(), c.patients(), c.requis(),
                        c.salleIsolement(), (int) c.presents().stream().filter(p -> !p.infirmierId().equals(infirmier)).count(),
                        occupants.getOrDefault(c.salleId() + "|" + c.creneauId() + "|" + c.jour().name(), List.of()).stream()
                                .map(o -> new Occupant(o.patient().valeur(), o.nom(), o.generateur(), o.aRisque(), o.libereLe(),
                                        o.temporaire(), o.realiseeHorsPlanning(), o.dejaRealiseeLe())).toList()))
                .toList();
    }

    private static String cle(Case c) {
        return c.salle().valeur() + "|" + c.creneau().valeur() + "|" + c.jour().name();
    }
}
