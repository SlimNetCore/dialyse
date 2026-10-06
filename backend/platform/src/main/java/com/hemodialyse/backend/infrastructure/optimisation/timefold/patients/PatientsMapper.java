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
import com.hemodialyse.backend.domain.planning.optimisation.service.SchemasJoursService;
import com.hemodialyse.backend.domain.planning.optimisation.service.SchemasJoursService.Schema;
import com.hemodialyse.backend.domain.planning.optimisation.service.VerificationDeplacementsService;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduit les données du centre en problème de placement et la solution en déplacements de patients. Les patients dont
 * aucun jour de dialyse n'est ouvert (ou, jours à choisir, sans schéma possible) ne sont pas planifiables : ils gardent
 * leur place (et sont signalés s'ils n'en ont pas).
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
            List<JourSemaine> actuels = p.jours().stream().sorted().toList();
            List<SchemaJours> schemas = schemas(p, ouverts);
            if (schemas.isEmpty()) continue;
            Poste actuel = p.actuelle();
            PlacementPatient entite = new PlacementPatient(p.patientId(), p.nom(), p.aRisque(), actuels, schemas,
                    actuel == null ? null : actuel.salleId(), actuel == null ? null : actuel.creneauId(),
                    actuel == null ? null : actuel.generateurId(), p.aRisque() ? postesIsolement : normaux,
                    p.creneauPrefereId(), p.transporteurs(), p.competencesRequises());
            if (actuel != null && actuel.generateurId() != null && actuel.creneauId() != null) {
                PosteSerie courant = parCle.get(actuel.generateurId() + "|" + actuel.creneauId());
                if (courant != null && courant.isolement() == p.aRisque()) entite.setPoste(courant);
            }
            patients.add(entite);
        }

        return new PlanPatients(contexte(donnees), patients, PoidsOptimisation.patients(parametres));
    }

    public static ContexteCentre contexte(DonneesOptimisation donnees) {
        int generateurs = donnees.planning().generateurs().size();
        return new ContexteCentre(donnees.presence().patientsParInfirmier(), generateurs,
                CapaciteTheoriqueCalculator.generateursDeSecours(generateurs));
    }

    /**
     * Schémas de jours candidats : les jours prescrits ouverts, ou ceux que l'optimisation peut choisir (le schéma actuel
     * reste candidat s'il a le bon nombre de séances, même un peu moins bien espacé).
     */
    static List<SchemaJours> schemas(PatientAPlacer p, Set<JourSemaine> ouverts) {
        if (!p.joursAChoisir()) {
            List<JourSemaine> jours = p.jours().stream().filter(ouverts::contains).sorted().toList();
            return jours.isEmpty() ? List.of() : List.of(SchemaJours.fixe(jours));
        }
        List<Schema> candidats = SchemasJoursService.candidats(p.seancesAChoisir(), ouverts);
        if (candidats.isEmpty()) return List.of();
        int meilleur = candidats.getFirst().espacement();
        List<SchemaJours> schemas = new ArrayList<>();
        boolean actuelPresent = false;
        for (Schema s : candidats) {
            schemas.add(new SchemaJours(List.copyOf(s.jours()), meilleur - s.espacement()));
            actuelPresent |= s.jours().equals(p.jours());
        }
        if (!actuelPresent && p.jours().size() == p.seancesAChoisir() && ouverts.containsAll(p.jours())) {
            int espacement = PlanificationAffectationService.scoreEspacement(p.jours(), p.seancesAChoisir());
            schemas.add(new SchemaJours(List.copyOf(p.jours()), Math.max(0, meilleur - espacement)));
        }
        return schemas;
    }

    /**
     * Déplacements et patients non placés d'une solution. Un patient que le solveur ne sait pas placer garde sa place
     * actuelle si elle est valide ; les déplacements qui violeraient une règle de planification une fois les autres
     * appliqués sont retirés (filet de sécurité du domaine).
     */
    public static Resultat versResultat(PlanPatients solution, DonneesOptimisation donnees) {
        return versResultat(solution.getPatients(), donnees);
    }

    /**
     * Comme {@link #versResultat(PlanPatients, DonneesOptimisation)}, à partir des entités résolues (modèle conjoint).
     * Un patient dont seuls les jours changent est un déplacement sur place, avec ses nouveaux jours.
     */
    public static Resultat versResultat(List<PlacementPatient> entites, DonneesOptimisation donnees) {
        Map<UUID, PlacementPatient> resolus = new HashMap<>();
        for (PlacementPatient e : entites) {
            if (e.getPoste() != null) resolus.put(e.getId(), e);
        }
        List<DeplacementPatient> deplacements = new ArrayList<>();
        for (PatientAPlacer p : donnees.patients()) {
            PlacementPatient resolu = resolus.get(p.patientId());
            if (resolu == null) continue;
            Poste vers = resolu.getPoste().versDomaine();
            Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
            jours.addAll(resolu.getJours());
            boolean joursChanges = p.joursAChoisir() && !jours.equals(p.jours());
            if (p.actuelle() == null || !vers.memePlaceQue(p.actuelle()) || joursChanges) {
                deplacements.add(new DeplacementPatient(p.patientId(), p.nom(), p.actuelle(), vers,
                        joursChanges ? resolu.getJours() : null));
            }
        }
        deplacements = VerificationDeplacementsService.retirerInvalides(donnees, deplacements);

        Map<UUID, Poste> placements = new LinkedHashMap<>(donnees.placementsActuels());
        Map<UUID, Set<JourSemaine>> joursChoisis = new LinkedHashMap<>();
        for (DeplacementPatient d : deplacements) {
            placements.put(d.patientId(), d.vers());
            if (d.jours() != null) joursChoisis.put(d.patientId(), EnumSet.copyOf(d.jours()));
        }

        boolean salleIsolement = donnees.planning().sallesIsolement() != null
                && !donnees.planning().sallesIsolement().isEmpty();
        Set<UUID> planifies = new HashSet<>();
        entites.forEach(e -> planifies.add(e.getId()));
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
        return new Resultat(deplacements, nonPlaces, placements, joursChoisis);
    }

    private static Set<JourSemaine> joursOuverts(DonneesPlanning planning) {
        return planning.joursOuverts() == null || planning.joursOuverts().isEmpty()
                ? EnumSet.allOf(JourSemaine.class) : EnumSet.copyOf(planning.joursOuverts());
    }

    /**
     * @param placements   place de chaque patient après application (places actuelles pour ceux qui ne bougent pas)
     * @param joursChoisis nouveaux jours des patients dont l'optimisation a choisi les jours
     */
    public record Resultat(List<DeplacementPatient> deplacements, List<PatientNonPlace> nonPlaces,
                           Map<UUID, Poste> placements, Map<UUID, Set<JourSemaine>> joursChoisis) {
    }
}
