package com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.patients;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Poste;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.CauseNonPlace;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.PatientNonPlace;
import com.hemodialyse.backend.contexts.planification.domain.SchemasDeJours;
import com.hemodialyse.backend.contexts.planification.domain.SchemasDeJours.Schema;
import com.hemodialyse.backend.contexts.planification.domain.VerificationDesDeplacements;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.PoidsOptimisation;

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
    public static PlanPatients versProbleme(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        RessourcesDuCentre ressources = donnees.ressources();
        Set<JourDeLaSemaine> ouverts = donnees.joursOuverts();

        List<PosteSerie> normaux = new ArrayList<>();
        List<PosteSerie> postesIsolement = new ArrayList<>();
        Map<String, PosteSerie> parCle = new HashMap<>();
        for (RessourcesDuCentre.Creneau creneau : ressources.creneaux()) {
            for (RessourcesDuCentre.Generateur g : ressources.generateurs()) {
                boolean iso = ressources.estIsolement(g.salle());
                PosteSerie poste = new PosteSerie(g.id().valeur(), g.code(), g.salle().valeur(), creneau.id().valeur(), iso);
                (iso ? postesIsolement : normaux).add(poste);
                parCle.put(g.id().valeur() + "|" + creneau.id().valeur(), poste);
            }
        }

        List<PlacementPatient> patients = new ArrayList<>();
        for (PatientAPlacer p : donnees.patients()) {
            List<JourDeLaSemaine> actuels = p.jours().stream().sorted().toList();
            List<SchemaJours> schemas = schemas(p, ouverts);
            if (schemas.isEmpty()) continue;
            Poste actuel = p.actuelle();
            UUID salle = actuel == null || actuel.salle() == null ? null : actuel.salle().valeur();
            UUID creneau = actuel == null || actuel.creneau() == null ? null : actuel.creneau().valeur();
            UUID generateur = actuel == null || actuel.generateur() == null ? null : actuel.generateur().valeur();
            PlacementPatient entite = new PlacementPatient(p.patient().valeur(), p.nom(), p.aRisque(), actuels, schemas,
                    salle, creneau, generateur, p.aRisque() ? postesIsolement : normaux,
                    p.creneauPrefere() == null ? null : p.creneauPrefere().valeur(), p.transporteurs(), p.competencesRequises());
            if (generateur != null && creneau != null) {
                PosteSerie courant = parCle.get(generateur + "|" + creneau);
                if (courant != null && courant.isolement() == p.aRisque()) entite.setPoste(courant);
            }
            patients.add(entite);
        }

        return new PlanPatients(contexte(donnees), patients, PoidsOptimisation.patients(parametres));
    }

    public static ContexteCentre contexte(DonneesDOptimisation donnees) {
        int generateurs = donnees.ressources().generateurs().size();
        return new ContexteCentre(donnees.personnel().ratio().patientsParInfirmier(), generateurs,
                com.hemodialyse.backend.contexts.planification.domain.GenerateursDeSecours.pour(generateurs));
    }

    /**
     * Schémas de jours candidats : les jours prescrits ouverts, ou ceux que l'optimisation peut choisir (le schéma actuel
     * reste candidat s'il a le bon nombre de séances, même un peu moins bien espacé).
     */
    static List<SchemaJours> schemas(PatientAPlacer p, Set<JourDeLaSemaine> ouverts) {
        if (!p.joursAChoisir()) {
            List<JourDeLaSemaine> jours = p.jours().stream().filter(ouverts::contains).sorted().toList();
            return jours.isEmpty() ? List.of() : List.of(SchemaJours.fixe(jours));
        }
        List<Schema> candidats = SchemasDeJours.candidats(p.seancesAChoisir(), ouverts);
        if (candidats.isEmpty()) return List.of();
        int meilleur = candidats.getFirst().espacement();
        List<SchemaJours> schemas = new ArrayList<>();
        boolean actuelPresent = false;
        for (Schema s : candidats) {
            schemas.add(new SchemaJours(List.copyOf(s.jours()), meilleur - s.espacement()));
            actuelPresent |= s.jours().equals(p.jours());
        }
        if (!actuelPresent && p.jours().size() == p.seancesAChoisir() && ouverts.containsAll(p.jours())) {
            int espacement = PropositionsDePlace.scoreEspacement(p.jours(), p.seancesAChoisir());
            schemas.add(new SchemaJours(List.copyOf(p.jours()), Math.max(0, meilleur - espacement)));
        }
        return schemas;
    }

    /**
     * Déplacements et patients non placés d'une solution. Un patient que le solveur ne sait pas placer garde sa place
     * actuelle si elle est valide ; les déplacements qui violeraient une règle de planification une fois les autres
     * appliqués sont retirés (filet de sécurité du domaine).
     */
    public static Resultat versResultat(PlanPatients solution, DonneesDOptimisation donnees) {
        return versResultat(solution.getPatients(), donnees);
    }

    /**
     * Comme {@link #versResultat(PlanPatients, DonneesDOptimisation)}, à partir des entités résolues (modèle conjoint).
     * Un patient dont seuls les jours changent est un déplacement sur place, avec ses nouveaux jours.
     */
    public static Resultat versResultat(List<PlacementPatient> entites, DonneesDOptimisation donnees) {
        Map<UUID, PlacementPatient> resolus = new HashMap<>();
        for (PlacementPatient e : entites) {
            if (e.getPoste() != null) resolus.put(e.getId(), e);
        }
        List<DeplacementDePatient> deplacements = new ArrayList<>();
        for (PatientAPlacer p : donnees.patients()) {
            PlacementPatient resolu = resolus.get(p.patient().valeur());
            if (resolu == null) continue;
            Poste vers = resolu.getPoste().versDomaine();
            Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
            jours.addAll(resolu.getJours());
            boolean joursChanges = p.joursAChoisir() && !jours.equals(p.jours());
            if (p.actuelle() == null || !vers.memePlaceQue(p.actuelle()) || joursChanges) {
                deplacements.add(new DeplacementDePatient(p.patient(), p.nom(), p.actuelle(), vers,
                        joursChanges ? resolu.getJours() : null));
            }
        }
        deplacements = VerificationDesDeplacements.retirerInvalides(donnees, deplacements);

        Map<PatientId, Poste> placements = new LinkedHashMap<>(donnees.placementsActuels());
        Map<PatientId, Set<JourDeLaSemaine>> joursChoisis = new LinkedHashMap<>();
        for (DeplacementDePatient d : deplacements) {
            placements.put(d.patient(), d.vers());
            if (d.jours() != null) {
                Set<JourDeLaSemaine> choisis = EnumSet.noneOf(JourDeLaSemaine.class);
                choisis.addAll(d.jours());
                joursChoisis.put(d.patient(), choisis);
            }
        }

        boolean salleIsolement = donnees.ressources().aUneSalleDIsolement();
        Set<UUID> planifies = new HashSet<>();
        entites.forEach(e -> planifies.add(e.getId()));
        List<PatientNonPlace> nonPlaces = new ArrayList<>();
        for (PatientAPlacer p : donnees.patients()) {
            Poste poste = placements.get(p.patient());
            boolean complet = poste != null && poste.estComplet();
            if (complet) continue;
            CauseNonPlace cause = !planifies.contains(p.patient().valeur()) ? CauseNonPlace.JOURS_FERMES
                    : p.aRisque() && !salleIsolement ? CauseNonPlace.ISOLEMENT_IMPOSSIBLE : CauseNonPlace.AUCUNE_PLACE;
            nonPlaces.add(new PatientNonPlace(p.patient(), p.nom(), cause));
        }
        return new Resultat(deplacements, nonPlaces, placements, joursChoisis);
    }

    /**
     * @param placements   place de chaque patient après application (places actuelles pour ceux qui ne bougent pas)
     * @param joursChoisis nouveaux jours des patients dont l'optimisation a choisi les jours
     */
    public record Resultat(List<DeplacementDePatient> deplacements, List<PatientNonPlace> nonPlaces,
                           Map<PatientId, Poste> placements, Map<PatientId, Set<JourDeLaSemaine>> joursChoisis) {
    }
}
