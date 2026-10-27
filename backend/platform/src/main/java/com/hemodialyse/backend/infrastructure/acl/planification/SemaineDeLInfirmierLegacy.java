package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.application.planning.PlanningSemaineQueryService;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.CellulePlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.OccupantPlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fournit au contexte Planification son port consommé {@link SemaineDeLInfirmier} (ACL vers la présence et le planning de la semaine
 * du legacy, slices P4 et P5, retirée avec elles). Traducteur des types legacy vers ceux du langage publié : mêmes noms, mêmes valeurs.
 * Aucune donnée nominative d'un autre infirmier n'est exposée : les patients sont ceux des salles et créneaux de l'infirmier.
 */
@Component
public class SemaineDeLInfirmierLegacy implements SemaineDeLInfirmier {

    private final PresenceDonneesPort donnees;
    private final PlanningSemaineQueryService planningSemaine;

    public SemaineDeLInfirmierLegacy(PresenceDonneesPort donnees, PlanningSemaineQueryService planningSemaine) {
        this.donnees = donnees;
        this.planningSemaine = planningSemaine;
    }

    @Override
    public PlanningDeLaSemaine semaine(UUID centre, UUID infirmier, LocalDate date) {
        LocalDate debut = PlanningSemaineService.debutSemaine(date);
        SemainePresence semaine = PresenceInfirmierService.construire(donnees.charger(centre, debut, debut.plusDays(6)), debut);
        List<Presence.CreneauPersonnel> mes = PresenceInfirmierService.creneauxDe(semaine, infirmier);
        SemainePlanning planning = planningSemaine.semaine(centre, date);
        return new PlanningDeLaSemaine(semaine.debut(), semaine.fin(),
                semaine.salles().stream().map(s -> new Salle(s.id(), s.nom())).toList(),
                semaine.creneaux().stream().map(c -> new Creneau(c.id(), c.libelle(), c.ordre())).toList(),
                mes.stream().map(c -> new CreneauPersonnel(c.date(), c.jour().name(), c.salleId(), c.creneauId(),
                        c.situation().name())).toList(),
                semaine.jours().stream().map(j -> new JourDuPlanning(j.jour().name(), j.date(), j.ouvertHebdomadaire(),
                        j.fermetureMotif())).toList(),
                mesCases(semaine, planning, mes, infirmier));
    }

    private static String cle(LocalDate date, UUID salleId, UUID creneauId) {
        return date + "|" + salleId + "|" + creneauId;
    }

    /**
     * Une ligne par case où l'infirmier est prévu, remplaçant ou absent : patients, effectif requis, collègues, occupants.
     */
    private static List<CaseDeLInfirmier> mesCases(SemainePresence semaine, SemainePlanning planning, List<Presence.CreneauPersonnel> mes,
                                                   UUID infirmier) {
        Set<String> miennes = mes.stream().map(c -> cle(c.date(), c.salleId(), c.creneauId())).collect(Collectors.toSet());
        Map<String, List<OccupantPlanning>> occupants = planning.cellules().stream().collect(Collectors.toMap(
                c -> c.salleId() + "|" + c.creneauId() + "|" + c.jour(), CellulePlanning::occupants, (a, b) -> a));
        return semaine.cases().stream()
                .filter(c -> miennes.contains(cle(c.date(), c.salleId(), c.creneauId())))
                .map(c -> new CaseDeLInfirmier(c.date(), c.jour().name(), c.salleId(), c.creneauId(), c.patients(), c.requis(),
                        c.salleIsolement(), (int) c.presents().stream().filter(p -> !p.infirmierId().equals(infirmier)).count(),
                        occupants.getOrDefault(c.salleId() + "|" + c.creneauId() + "|" + c.jour(), List.of()).stream()
                                .map(o -> new Occupant(o.patientId(), o.nom(), o.generateurCode(), o.aRisque(), o.libereLe(),
                                        o.temporaire(), o.realiseeHorsPlanning(), o.dejaRealiseeLe())).toList()))
                .toList();
    }
}
