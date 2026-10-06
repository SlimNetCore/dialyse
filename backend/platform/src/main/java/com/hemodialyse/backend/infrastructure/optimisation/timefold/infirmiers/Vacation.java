package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Entité planifiée : une vacation d'infirmier à pourvoir sur une case (salle, créneau, date). Une vacation « épinglée »
 * est déjà tenue (roulement ou remplacement en place) : le solveur la compte mais ne la déplace pas. Une valeur nulle
 * signifie « non pourvue ».
 */
@PlanningEntity
public class Vacation {

    @PlanningId
    private String id;
    private LocalDate date;
    private JourSemaine jour;
    private int semaine;
    private UUID salleId;
    private UUID creneauId;
    private boolean prefereQualifie;

    @PlanningPin
    private boolean epinglee;

    @ValueRangeProvider(id = "candidats")
    private List<InfirmierPlan> candidats;

    @PlanningVariable(valueRangeProviderRefs = "candidats", allowsUnassigned = true)
    private InfirmierPlan infirmier;

    public Vacation() {
    }

    public Vacation(String id, LocalDate date, JourSemaine jour, int semaine, UUID salleId, UUID creneauId,
                    boolean prefereQualifie, boolean epinglee, List<InfirmierPlan> candidats, InfirmierPlan infirmier) {
        this.id = id;
        this.date = date;
        this.jour = jour;
        this.semaine = semaine;
        this.salleId = salleId;
        this.creneauId = creneauId;
        this.prefereQualifie = prefereQualifie;
        this.epinglee = epinglee;
        this.candidats = candidats;
        this.infirmier = infirmier;
    }

    /**
     * Clé « salle|créneau|jour » comparable aux places exactes du roulement actuel.
     */
    public String cleExacte() {
        return salleId + "|" + creneauId + "|" + jour;
    }

    public String getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public JourSemaine getJour() {
        return jour;
    }

    public int getSemaine() {
        return semaine;
    }

    public UUID getSalleId() {
        return salleId;
    }

    public UUID getCreneauId() {
        return creneauId;
    }

    public boolean isPrefereQualifie() {
        return prefereQualifie;
    }

    public boolean isEpinglee() {
        return epinglee;
    }

    public List<InfirmierPlan> getCandidats() {
        return candidats;
    }

    public InfirmierPlan getInfirmier() {
        return infirmier;
    }

    public void setInfirmier(InfirmierPlan infirmier) {
        this.infirmier = infirmier;
    }
}
