package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.util.List;
import java.util.UUID;

/**
 * Entité planifiée : le placement d'un patient. Ses jours de dialyse sont prescrits (donnée) ; le solveur choisit son
 * générateur et son créneau. Une valeur nulle signifie « non placé ».
 */
@PlanningEntity
public class PlacementPatient {

    @PlanningId
    private UUID id;
    private String nom;
    private boolean aRisque;
    private int joursMask;
    private List<JourSemaine> jours;
    private UUID salleActuelle;
    private UUID creneauActuel;
    private UUID generateurActuel;

    @ValueRangeProvider(id = "postesCompatibles")
    private List<PosteSerie> postesCompatibles;

    @PlanningVariable(valueRangeProviderRefs = "postesCompatibles", allowsUnassigned = true)
    private PosteSerie poste;

    public PlacementPatient() {
    }

    public PlacementPatient(UUID id, String nom, boolean aRisque, List<JourSemaine> jours, UUID salleActuelle,
                            UUID creneauActuel, UUID generateurActuel, List<PosteSerie> postesCompatibles) {
        this.id = id;
        this.nom = nom;
        this.aRisque = aRisque;
        this.jours = List.copyOf(jours);
        this.joursMask = masque(jours);
        this.salleActuelle = salleActuelle;
        this.creneauActuel = creneauActuel;
        this.generateurActuel = generateurActuel;
        this.postesCompatibles = postesCompatibles;
    }

    public static int masque(List<JourSemaine> jours) {
        int mask = 0;
        for (JourSemaine jour : jours) mask |= 1 << jour.ordinal();
        return mask;
    }

    /**
     * Les deux patients dialysent-ils un même jour (et se disputeraient donc un même générateur) ?
     */
    public boolean partageUnJour(PlacementPatient autre) {
        return (joursMask & autre.joursMask) != 0;
    }

    /**
     * Coût du changement de place : créneau (3), salle (2), générateur seul (1), 0 si rien ne change ou si le patient
     * n'avait pas de place de référence.
     */
    public int coutChangement() {
        if (poste == null) return 0;
        if (creneauActuel != null && !creneauActuel.equals(poste.creneauId())) return 3;
        if (salleActuelle != null && !salleActuelle.equals(poste.salleId())) return 2;
        if (generateurActuel != null && !generateurActuel.equals(poste.generateurId())) return 1;
        return 0;
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public boolean isARisque() {
        return aRisque;
    }

    public int getJoursMask() {
        return joursMask;
    }

    public List<JourSemaine> getJours() {
        return jours;
    }

    public UUID getSalleActuelle() {
        return salleActuelle;
    }

    public UUID getCreneauActuel() {
        return creneauActuel;
    }

    public UUID getGenerateurActuel() {
        return generateurActuel;
    }

    public List<PosteSerie> getPostesCompatibles() {
        return postesCompatibles;
    }

    public PosteSerie getPoste() {
        return poste;
    }

    public void setPoste(PosteSerie poste) {
        this.poste = poste;
    }
}
