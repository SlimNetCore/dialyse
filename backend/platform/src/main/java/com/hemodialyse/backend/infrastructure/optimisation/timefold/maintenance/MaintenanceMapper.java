package com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance;

import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.SeanceSansSolution;
import com.hemodialyse.backend.domain.planning.optimisation.service.MaintenanceGenerateursService;
import com.hemodialyse.backend.domain.planning.optimisation.service.MaintenanceGenerateursService.SeanceImpactee;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PosteSerie;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduit les séances touchées par une maintenance en problème de déplacements temporaires, et la solution en
 * déplacements proposés ou séances sans solution. Places possibles d'une séance : générateurs disponibles ce jour-là,
 * de la bonne catégorie (isolement ou non), qu'aucun autre patient n'occupe à ce créneau.
 */
public final class MaintenanceMapper {

    private MaintenanceMapper() {
    }

    public static PlanMaintenance versProbleme(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        List<SeanceImpactee> impactees = MaintenanceGenerateursService.seancesImpactees(donnees,
                parametres.debutSemaine(), parametres.finHorizon());
        Map<LocalDate, List<SeanceImpactee>> parDate = new LinkedHashMap<>();
        impactees.forEach(s -> parDate.computeIfAbsent(s.date(), k -> new ArrayList<>()).add(s));
        Set<UUID> isolement = donnees.planning().sallesIsolement() == null ? Set.of()
                : donnees.planning().sallesIsolement();

        List<SeanceTemporaire> seances = new ArrayList<>();
        parDate.forEach((date, duJour) -> {
            Set<UUID> deplaces = new HashSet<>();
            duJour.forEach(s -> deplaces.add(s.patientId()));
            Set<String> occupes = MaintenanceGenerateursService.postesOccupes(donnees, date, deplaces);
            List<PosteSerie> normaux = new ArrayList<>();
            List<PosteSerie> postesIsolement = new ArrayList<>();
            for (GenerateurRef g : donnees.planning().generateurs()) {
                if (MaintenanceGenerateursService.indisponibilite(donnees, g.id(), date).isPresent()) continue;
                boolean iso = isolement.contains(g.salleId());
                for (CreneauRef creneau : donnees.planning().creneaux()) {
                    if (occupes.contains(MaintenanceGenerateursService.cle(g.id(), creneau.id()))) continue;
                    (iso ? postesIsolement : normaux).add(new PosteSerie(g.id(), g.code(), g.salleId(), creneau.id(), iso));
                }
            }
            for (SeanceImpactee s : duJour) {
                seances.add(new SeanceTemporaire(s.patientId(), date, s.jour(), s.poste(), s.motif(),
                        s.aRisque() ? postesIsolement : normaux));
            }
        });
        return new PlanMaintenance(seances, PoidsOptimisation.maintenance());
    }

    public static Resultat versResultat(PlanMaintenance solution, DonneesOptimisation donnees) {
        Map<UUID, String> noms = new HashMap<>();
        for (PatientAPlacer p : donnees.patients()) noms.put(p.patientId(), p.nom());
        List<DeplacementTemporairePropose> deplacements = new ArrayList<>();
        List<SeanceSansSolution> sansSolution = new ArrayList<>();
        for (SeanceTemporaire s : solution.getSeances()) {
            String nom = noms.getOrDefault(s.getPatientId(), "");
            if (s.getPoste() != null) {
                deplacements.add(new DeplacementTemporairePropose(s.getPatientId(), nom, s.getDate(), s.getJour(),
                        s.getHabituel(), s.getPoste().versDomaine(), s.getMotif()));
            } else {
                sansSolution.add(new SeanceSansSolution(s.getPatientId(), nom, s.getDate(), s.getJour(),
                        s.getHabituel(), s.getMotif()));
            }
        }
        deplacements.sort(Comparator.comparing(DeplacementTemporairePropose::date)
                .thenComparing(DeplacementTemporairePropose::nom));
        sansSolution.sort(Comparator.comparing(SeanceSansSolution::date).thenComparing(SeanceSansSolution::nom));
        return new Resultat(deplacements, sansSolution);
    }

    public record Resultat(List<DeplacementTemporairePropose> deplacements, List<SeanceSansSolution> sansSolution) {
    }
}
