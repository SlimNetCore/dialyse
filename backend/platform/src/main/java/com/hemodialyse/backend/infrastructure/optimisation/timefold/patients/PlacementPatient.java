package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entité planifiée : le placement d'un patient. Le solveur choisit son générateur et son créneau (valeur nulle :
 * « non placé ») et ses jours de dialyse parmi ses schémas candidats — un seul, ses jours prescrits, sauf si sa
 * préférence demande à l'optimisation de les choisir.
 */
@PlanningEntity
public class PlacementPatient {

    @PlanningId
    private UUID id;
    private String nom;
    private boolean aRisque;
    private int masqueActuel;
    private UUID salleActuelle;
    private UUID creneauActuel;
    private UUID generateurActuel;
    private UUID creneauPrefere;
    private Set<UUID> transporteurs;
    private Set<CompetenceInfirmier> competences;

    @ValueRangeProvider(id = "postesCompatibles")
    private List<PosteSerie> postesCompatibles;

    @ValueRangeProvider(id = "schemasCandidats")
    private List<SchemaJours> schemasCandidats;

    @PlanningVariable(valueRangeProviderRefs = "postesCompatibles", allowsUnassigned = true)
    private PosteSerie poste;

    @PlanningVariable(valueRangeProviderRefs = "schemasCandidats")
    private SchemaJours schema;

    public PlacementPatient() {
    }

    /**
     * Patient aux jours prescrits, sans préférence.
     */
    public PlacementPatient(UUID id, String nom, boolean aRisque, List<JourSemaine> jours, UUID salleActuelle,
                            UUID creneauActuel, UUID generateurActuel, List<PosteSerie> postesCompatibles) {
        this(id, nom, aRisque, jours, List.of(SchemaJours.fixe(jours)), salleActuelle, creneauActuel,
                generateurActuel, postesCompatibles, null, Set.of(), Set.of());
    }

    /**
     * @param joursActuels     jours de dialyse actuels (référence de stabilité ; vides pour un nouveau patient)
     * @param schemasCandidats schémas de jours possibles (au moins un)
     */
    public PlacementPatient(UUID id, String nom, boolean aRisque, List<JourSemaine> joursActuels,
                            List<SchemaJours> schemasCandidats, UUID salleActuelle, UUID creneauActuel,
                            UUID generateurActuel, List<PosteSerie> postesCompatibles, UUID creneauPrefere,
                            Set<UUID> transporteurs, Set<CompetenceInfirmier> competences) {
        this.id = id;
        this.nom = nom;
        this.aRisque = aRisque;
        this.masqueActuel = masque(joursActuels);
        this.schemasCandidats = List.copyOf(schemasCandidats);
        this.salleActuelle = salleActuelle;
        this.creneauActuel = creneauActuel;
        this.generateurActuel = generateurActuel;
        this.postesCompatibles = postesCompatibles;
        this.creneauPrefere = creneauPrefere;
        this.transporteurs = transporteurs == null ? Set.of() : Set.copyOf(transporteurs);
        this.competences = competences == null ? Set.of() : Set.copyOf(competences);
        if (this.schemasCandidats.size() == 1) this.schema = this.schemasCandidats.getFirst();
        else {
            for (SchemaJours s : this.schemasCandidats) {
                if (s.masque() == masqueActuel) this.schema = s;
            }
        }
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
        return (getJoursMask() & autre.getJoursMask()) != 0;
    }

    /**
     * Jours communs aux deux patients.
     */
    public int joursCommuns(PlacementPatient autre) {
        return Integer.bitCount(getJoursMask() & autre.getJoursMask());
    }

    /**
     * Les deux patients partagent-ils un transporteur (aller ou retour) ?
     */
    public boolean partageUnTransporteur(PlacementPatient autre) {
        return !transporteurs.isEmpty() && !Collections.disjoint(transporteurs, autre.transporteurs);
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

    /**
     * Jours de dialyse actuels abandonnés par le schéma choisi (0 pour un nouveau patient).
     */
    public int joursChanges() {
        if (masqueActuel == 0) return 0;
        return Integer.bitCount(masqueActuel & ~getJoursMask());
    }

    public boolean horsCreneauPrefere() {
        return creneauPrefere != null && poste != null && !creneauPrefere.equals(poste.creneauId());
    }

    public int penaliteEspacement() {
        return schema == null ? 0 : schema.penaliteEspacement();
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
        return schema == null ? 0 : schema.masque();
    }

    public List<JourSemaine> getJours() {
        return schema == null ? List.of() : schema.jours();
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

    public Set<CompetenceInfirmier> getCompetences() {
        return competences;
    }

    public List<PosteSerie> getPostesCompatibles() {
        return postesCompatibles;
    }

    public List<SchemaJours> getSchemasCandidats() {
        return schemasCandidats;
    }

    public PosteSerie getPoste() {
        return poste;
    }

    public void setPoste(PosteSerie poste) {
        this.poste = poste;
    }

    public SchemaJours getSchema() {
        return schema;
    }

    public void setSchema(SchemaJours schema) {
        this.schema = schema;
    }
}
