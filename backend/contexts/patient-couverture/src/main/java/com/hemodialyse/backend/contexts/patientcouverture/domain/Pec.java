package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class Pec {
    private final PecId id;
    private final CentreId centreId;
    private final PatientId patientId;

    private LocalDate dateDebutDemande;
    private LocalDate dateFinDemande;
    private UUID forfaitDemandeId;

    private LocalDate dateDebutEffectif;
    private LocalDate dateFinEffectif;
    private UUID forfaitEffectifId;

    private StatutPec statut;
    private final OffsetDateTime createdAt;

    public Pec(PecId id, CentreId centreId, PatientId patientId,
               LocalDate dateDebutDemande, LocalDate dateFinDemande, UUID forfaitDemandeId,
               LocalDate dateDebutEffectif, LocalDate dateFinEffectif, UUID forfaitEffectifId,
               StatutPec statut, OffsetDateTime createdAt) {
        if (id == null) throw new IllegalArgumentException("Pec requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        this.id = id;
        this.centreId = centreId;
        this.patientId = patientId;
        this.dateDebutDemande = dateDebutDemande;
        this.dateFinDemande = dateFinDemande;
        this.forfaitDemandeId = forfaitDemandeId;
        this.dateDebutEffectif = dateDebutEffectif;
        this.dateFinEffectif = dateFinEffectif;
        this.forfaitEffectifId = forfaitEffectifId;
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt requis");
        this.statut = statut == null ? StatutPec.CREE : statut;
        this.createdAt = createdAt;
        if (dateDebutDemande != null && dateFinDemande != null && dateDebutDemande.isAfter(dateFinDemande)) {
            throw new IllegalArgumentException("PeriodeIncoherente");
        }
    }

    public static Pec creer(PecId id, PatientId patientId, CentreId centreId,
                            LocalDate dateDebutDemande, LocalDate dateFinDemande, UUID forfaitDemandeId,
                            OffsetDateTime createdAt) {
        return new Pec(id, centreId, patientId, dateDebutDemande, dateFinDemande, forfaitDemandeId,
                null, null, null, StatutPec.CREE, createdAt);
    }

    public void valider(LocalDate dateDebutEffectif, LocalDate dateFinEffectif, UUID forfaitEffectifId) {
        if (statut != StatutPec.CREE) throw new IllegalStateException("PECDejaValidee");
        if (dateDebutEffectif != null && dateFinEffectif != null && dateDebutEffectif.isAfter(dateFinEffectif)) {
            throw new IllegalArgumentException("PeriodeIncoherente");
        }
        this.dateDebutEffectif = dateDebutEffectif;
        this.dateFinEffectif = dateFinEffectif;
        this.forfaitEffectifId = forfaitEffectifId;
        this.statut = StatutPec.VALIDEE;
    }

    public void cloturer() {
        if (statut != StatutPec.VALIDEE) throw new IllegalStateException("PECDejaCloturee");
        this.statut = StatutPec.CLOTUREE;
    }

    public boolean autoriseSeance() {
        return statut == StatutPec.VALIDEE;
    }

    public boolean demandeIdentique(LocalDate debut, LocalDate fin, UUID forfaitId) {
        return Objects.equals(dateDebutDemande, debut) && Objects.equals(dateFinDemande, fin)
                && Objects.equals(forfaitDemandeId, forfaitId);
    }

    public boolean reviserDemande(LocalDate debut, LocalDate fin, UUID forfaitId) {
        if (demandeIdentique(debut, fin, forfaitId)) return false;
        if (debut != null && fin != null && debut.isAfter(fin))
            throw new IllegalArgumentException("PeriodeIncoherente");
        this.dateDebutDemande = debut;
        this.dateFinDemande = fin;
        this.forfaitDemandeId = forfaitId;
        return true;
    }

    public PecId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public PatientId patientId() {
        return patientId;
    }

    public LocalDate dateDebutDemande() {
        return dateDebutDemande;
    }

    public LocalDate dateFinDemande() {
        return dateFinDemande;
    }

    public UUID forfaitDemandeId() {
        return forfaitDemandeId;
    }

    public LocalDate dateDebutEffectif() {
        return dateDebutEffectif;
    }

    public LocalDate dateFinEffectif() {
        return dateFinEffectif;
    }

    public UUID forfaitEffectifId() {
        return forfaitEffectifId;
    }

    public StatutPec statut() {
        return statut;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}