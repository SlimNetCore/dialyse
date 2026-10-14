package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan de maintenance préventive d'un équipement (racine d'agrégat). Garantit la périodicité : exécution d'un plan
 * actif seulement (R25), suspension d'un plan actif et reprise d'un plan suspendu avec nouvelle date (R26).
 * L'heure arrive par paramètre ; aucune dépendance technique.
 */
public final class PlanMaintenance {

    private final PlanMaintenanceId id;
    private final EquipementId equipementId;
    private final CentreId centreId;
    private final String designation;
    private final String description;
    private final FrequenceMaintenance frequence;
    private final String tachesAEffectuer;
    private final Instant creeLe;
    private final UtilisateurId creePar;

    private StatutPlan statut;
    private Instant prochaineDatePrevue;
    private Instant derniereExecution;
    private int nombreExecutions;
    private Instant modifieLe;
    private UtilisateurId modifiePar;

    private final List<EvenementDePlanMaintenance> evenements = new ArrayList<>();

    private PlanMaintenance(PlanMaintenanceId id, EquipementId equipementId, CentreId centreId, String designation,
                            String description, FrequenceMaintenance frequence, StatutPlan statut,
                            Instant prochaineDatePrevue, Instant derniereExecution, int nombreExecutions,
                            String tachesAEffectuer, Instant creeLe, Instant modifieLe, UtilisateurId creePar,
                            UtilisateurId modifiePar) {
        this.id = id;
        this.equipementId = equipementId;
        this.centreId = centreId;
        this.designation = designation;
        this.description = description;
        this.frequence = frequence;
        this.statut = statut;
        this.prochaineDatePrevue = prochaineDatePrevue;
        this.derniereExecution = derniereExecution;
        this.nombreExecutions = nombreExecutions;
        this.tachesAEffectuer = tachesAEffectuer;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
        this.creePar = creePar;
        this.modifiePar = modifiePar;
    }

    /** Établit un plan actif. La date prévue dans le passé est acceptée (KB-GMAO-14 : le plan est alors en retard). */
    public static PlanMaintenance etablir(PlanMaintenanceId id, EquipementId equipementId, CentreId centreId,
                                          String designation, String description, FrequenceMaintenance frequence,
                                          Instant prochaineDatePrevue, String tachesAEffectuer, UtilisateurId par,
                                          Instant maintenant) {
        if (equipementId == null) throw new IllegalArgumentException("Équipement requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (designation == null || designation.isBlank()) throw new IllegalArgumentException("Désignation requise");
        if (frequence == null) throw new IllegalArgumentException("Fréquence requise");
        if (prochaineDatePrevue == null) throw new IllegalArgumentException("Prochaine date prévue requise");

        PlanMaintenance plan = new PlanMaintenance(id, equipementId, centreId, designation, description, frequence,
                StatutPlan.ACTIF, prochaineDatePrevue, null, 0, tachesAEffectuer, maintenant, null, par, null);
        plan.evenements.add(new PlanMaintenanceEtabli(id, equipementId, centreId, frequence, prochaineDatePrevue, par,
                maintenant));
        return plan;
    }

    /** Reconstitution depuis la persistance (adapters seulement) : aucun événement, aucune règle rejouée. */
    public static PlanMaintenance reconstituer(PlanMaintenanceId id, EquipementId equipementId, CentreId centreId,
                                               String designation, String description, FrequenceMaintenance frequence,
                                               StatutPlan statut, Instant prochaineDatePrevue,
                                               Instant derniereExecution, int nombreExecutions,
                                               String tachesAEffectuer, Instant creeLe, Instant modifieLe,
                                               UtilisateurId creePar, UtilisateurId modifiePar) {
        return new PlanMaintenance(id, equipementId, centreId, designation, description, frequence, statut,
                prochaineDatePrevue, derniereExecution, nombreExecutions, tachesAEffectuer, creeLe, modifieLe, creePar,
                modifiePar);
    }

    /**
     * Enregistre l'exécution d'un plan actif. La prochaine date est calculée depuis l'instant de l'exécution, pas
     * depuis la date prévue (KB-GMAO-12, reproduit du legacy).
     */
    public void enregistrerExecution(UtilisateurId par, Instant maintenant) {
        if (statut != StatutPlan.ACTIF) throw PlanNonActif.pourExecuter();
        derniereExecution = maintenant;
        nombreExecutions++;
        prochaineDatePrevue = frequence.prochaineDate(maintenant);
        marquerModifie(par, maintenant);
        evenements.add(new MaintenancePreventiveExecutee(id, centreId, prochaineDatePrevue, nombreExecutions, par,
                maintenant));
    }

    /** Suspend un plan actif (legacy : « désactiver »). */
    public void suspendre(UtilisateurId par, Instant maintenant) {
        if (statut != StatutPlan.ACTIF) throw PlanNonActif.pourSuspendre();
        statut = StatutPlan.INACTIF;
        marquerModifie(par, maintenant);
        evenements.add(new PlanMaintenanceSuspendu(id, centreId, par, maintenant));
    }

    /** Reprend un plan suspendu avec une nouvelle date prévue (legacy : « réactiver »). */
    public void reprendre(Instant nouvelleDatePrevue, UtilisateurId par, Instant maintenant) {
        if (statut != StatutPlan.INACTIF) throw new PlanNonSuspendu();
        if (nouvelleDatePrevue == null) throw new IllegalArgumentException("Nouvelle date prévue requise");
        statut = StatutPlan.ACTIF;
        prochaineDatePrevue = nouvelleDatePrevue;
        marquerModifie(par, maintenant);
        evenements.add(new PlanMaintenanceRepris(id, centreId, nouvelleDatePrevue, par, maintenant));
    }

    /** R27 : un plan actif dont la prochaine date prévue est atteinte ou dépassée est en retard (borne incluse). */
    public boolean estEnRetard(Instant maintenant) {
        return statut == StatutPlan.ACTIF && !prochaineDatePrevue.isAfter(maintenant);
    }

    /** Retire et retourne les événements levés depuis la dernière publication. */
    public List<EvenementDePlanMaintenance> tirerEvenements() {
        List<EvenementDePlanMaintenance> tires = List.copyOf(evenements);
        evenements.clear();
        return tires;
    }

    private void marquerModifie(UtilisateurId par, Instant maintenant) {
        modifieLe = maintenant;
        modifiePar = par;
    }

    public PlanMaintenanceId id() {
        return id;
    }

    public EquipementId equipementId() {
        return equipementId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String designation() {
        return designation;
    }

    public String description() {
        return description;
    }

    public FrequenceMaintenance frequence() {
        return frequence;
    }

    public StatutPlan statut() {
        return statut;
    }

    public Instant prochaineDatePrevue() {
        return prochaineDatePrevue;
    }

    public Instant derniereExecution() {
        return derniereExecution;
    }

    public int nombreExecutions() {
        return nombreExecutions;
    }

    public String tachesAEffectuer() {
        return tachesAEffectuer;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }

    public UtilisateurId creePar() {
        return creePar;
    }

    public UtilisateurId modifiePar() {
        return modifiePar;
    }
}
