package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.CauseNonPlace;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.PatientNonPlace;
import com.hemodialyse.backend.domain.planning.optimisation.service.VerificationDeplacementsService;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduit les données du centre en problème de placement et la solution en déplacements de patients. Les patients dont
 * aucun jour de dialyse n'est ouvert ne sont pas planifiables : ils gardent leur place (et sont signalés s'ils n'en ont
 * pas).
 */
public final class PatientsMapper {

    private PatientsMapper() {
    }

    /**
     * Problème de placement, démarré depuis les places actuelles (le « ne rien changer » est la solution de départ).
     */
    public static PlanPatients versProbleme(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        DonneesPlanning planning = donnees.planning();
        Set<UUID> isolement = planning.sallesIsolement() == null ? Set.of() : planning.sallesIsolement();
        Set<JourSemaine> ouverts = joursOuverts(planning);

        List<PosteSerie> normaux = new ArrayList<>();
        List<PosteSerie> postesIsolement = new ArrayList<>();
        Map<String, PosteSerie> parCle = new HashMap<>();
        for (CreneauRef creneau : planning.creneaux()) {
            for (GenerateurRef g : planning.generateurs()) {
                boolean iso = isolement.contains(g.salleId());
                PosteSerie poste = new PosteSerie(g.id(), g.code(), g.salleId(), creneau.id(), iso);
                (iso ? postesIsolement : normaux).add(poste);
                parCle.put(g.id() + "|" + creneau.id(), poste);
            }
        }

        List<PlacementPatient> patients = new ArrayList<>();
        for (PatientAPlacer p : donnees.patients()) {
            List<JourSemaine> jours = p.jours().stream().filter(ouverts::contains).sorted().toList();
            if (jours.isEmpty()) continue;
            Poste actuel = p.actuelle();
            PlacementPatient entite = new PlacementPatient(p.patientId(), p.nom(), p.aRisque(), jours,
                    actuel == null ? null : actuel.salleId(), actuel == null ? null : actuel.creneauId(),
                    actuel == null ? null : actuel.generateurId(), p.aRisque() ? postesIsolement : normaux);
            if (actuel != null && actuel.generateurId() != null && actuel.creneauId() != null) {
                PosteSerie courant = parCle.get(actuel.generateurId() + "|" + actuel.creneauId());
                if (courant != null && courant.isolement() == p.aRisque()) entite.setPoste(courant);
            }
            patients.add(entite);
        }

        int generateurs = planning.generateurs().size();
        ContexteCentre contexte = new ContexteCentre(donnees.presence().patientsParInfirmier(), generateurs,
                CapaciteTheoriqueCalculator.generateursDeSecours(generateurs));
        return new PlanPatients(contexte, patients, PoidsOptimisation.patients(parametres));
    }

    /**
     * Déplacements et patients non placés d'une solution. Un patient que le solveur ne sait pas placer garde sa place
     * actuelle si elle est valide ; les déplacements qui violeraient une règle de planification une fois les autres
     * appliqués sont retirés (filet de sécurité du domaine).
     */
    public static Resultat versResultat(PlanPatients solution, DonneesOptimisation donnees) {
        Map<UUID, PosteSerie> resolus = new HashMap<>();
        for (PlacementPatient e : solution.getPatients()) {
            if (e.getPoste() != null) resolus.put(e.getId(), e.getPoste());
        }
        List<DeplacementPatient> deplacements = new ArrayList<>();
        for (PatientAPlacer p : donnees.patients()) {
            PosteSerie nouveau = resolus.get(p.patientId());
            if (nouveau == null) continue;
            Poste vers = nouveau.versDomaine();
            if (p.actuelle() == null || !vers.memePlaceQue(p.actuelle())) {
                deplacements.add(new DeplacementPatient(p.patientId(), p.nom(), p.actuelle(), vers));
            }
        }
        deplacements = VerificationDeplacementsService.retirerInvalides(donnees, deplacements);

        Map<UUID, Poste> placements = new LinkedHashMap<>(donnees.placementsActuels());
        deplacements.forEach(d -> placements.put(d.patientId(), d.vers()));

        boolean salleIsolement = donnees.planning().sallesIsolement() != null
                && !donnees.planning().sallesIsolement().isEmpty();
        Set<UUID> planifies = new java.util.HashSet<>();
        solution.getPatients().forEach(e -> planifies.add(e.getId()));
        List<PatientNonPlace> nonPlaces = new ArrayList<>();
        for (PatientAPlacer p : donnees.patients()) {
            Poste poste = placements.get(p.patientId());
            boolean complet = poste != null && poste.salleId() != null && poste.creneauId() != null
                    && poste.generateurId() != null;
            if (complet) continue;
            CauseNonPlace cause = !planifies.contains(p.patientId()) ? CauseNonPlace.JOURS_FERMES
                    : p.aRisque() && !salleIsolement ? CauseNonPlace.ISOLEMENT_IMPOSSIBLE : CauseNonPlace.AUCUNE_PLACE;
            nonPlaces.add(new PatientNonPlace(p.patientId(), p.nom(), cause));
        }
        return new Resultat(deplacements, nonPlaces, placements);
    }

    private static Set<JourSemaine> joursOuverts(DonneesPlanning planning) {
        return planning.joursOuverts() == null || planning.joursOuverts().isEmpty()
                ? EnumSet.allOf(JourSemaine.class) : EnumSet.copyOf(planning.joursOuverts());
    }

    /**
     * @param placements place de chaque patient après application (places actuelles pour ceux qui ne bougent pas)
     */
    public record Resultat(List<DeplacementPatient> deplacements, List<PatientNonPlace> nonPlaces,
                           Map<UUID, Poste> placements) {
    }
}
