package com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PosteSerie;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Entité planifiée : une séance datée dont le générateur est indisponible. Le solveur lui choisit une place libre ce
 * jour-là (valeur nulle : aucune solution, séance à organiser).
 */
@PlanningEntity
public class SeanceTemporaire {

    @PlanningId
    private String id;
    private UUID patientId;
    private LocalDate date;
    private JourSemaine jour;
    private Poste habituel;
    private String motif;

    @ValueRangeProvider(id = "postesLibres")
    private List<PosteSerie> postesLibres;

    @PlanningVariable(valueRangeProviderRefs = "postesLibres", allowsUnassigned = true)
    private PosteSerie poste;

    public SeanceTemporaire() {
    }

    public SeanceTemporaire(UUID patientId, LocalDate date, JourSemaine jour, Poste habituel, String motif,
                            List<PosteSerie> postesLibres) {
        this.id = patientId + "|" + date;
        this.patientId = patientId;
        this.date = date;
        this.jour = jour;
        this.habituel = habituel;
        this.motif = motif;
        this.postesLibres = postesLibres;
    }

    public boolean changeDeCreneau() {
        return poste != null && habituel.creneauId() != null && !habituel.creneauId().equals(poste.creneauId());
    }

    public boolean changeDeSalle() {
        return poste != null && habituel.salleId() != null && !habituel.salleId().equals(poste.salleId());
    }

    public String getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public LocalDate getDate() {
        return date;
    }

    public JourSemaine getJour() {
        return jour;
    }

    public Poste getHabituel() {
        return habituel;
    }

    public String getMotif() {
        return motif;
    }

    public List<PosteSerie> getPostesLibres() {
        return postesLibres;
    }

    public PosteSerie getPoste() {
        return poste;
    }

    public void setPoste(PosteSerie poste) {
        this.poste = poste;
    }
}
