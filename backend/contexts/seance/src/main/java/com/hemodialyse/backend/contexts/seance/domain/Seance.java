package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class Seance {
    private final SeanceId id;
    private final CentreId centreId;
    private final PatientId patientId;
    private final PecId pecId;
    private final LocalDate dateSeance;
    private final String typeSeance;
    private StatutSeance statut;
    private boolean verrouilleFacturation;
    private final OffsetDateTime createdAt;
    private OffsetDateTime regularisationDeverrouilleeAt;
    private String regularisationDeverrouilleeBy;
    private ForfaitApplique forfaitSurcharge;

    public Seance(SeanceId id, CentreId centreId, PatientId patientId, PecId pecId,
                  LocalDate dateSeance, String typeSeance, StatutSeance statut,
                  boolean verrouilleFacturation, OffsetDateTime createdAt) {
        this(id, centreId, patientId, pecId, dateSeance, typeSeance, statut, verrouilleFacturation, createdAt, null, null);
    }

    public Seance(SeanceId id, CentreId centreId, PatientId patientId, PecId pecId,
                  LocalDate dateSeance, String typeSeance, StatutSeance statut,
                  boolean verrouilleFacturation, OffsetDateTime createdAt,
                  OffsetDateTime regularisationDeverrouilleeAt, String regularisationDeverrouilleeBy) {
        this(id, centreId, patientId, pecId, dateSeance, typeSeance, statut, verrouilleFacturation, createdAt,
                regularisationDeverrouilleeAt, regularisationDeverrouilleeBy, null);
    }

    public Seance(SeanceId id, CentreId centreId, PatientId patientId, PecId pecId,
                  LocalDate dateSeance, String typeSeance, StatutSeance statut,
                  boolean verrouilleFacturation, OffsetDateTime createdAt,
                  OffsetDateTime regularisationDeverrouilleeAt, String regularisationDeverrouilleeBy,
                  ForfaitApplique forfaitSurcharge) {
        if (id == null) throw new IllegalArgumentException("SeanceId requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        if (pecId == null) throw new IllegalArgumentException("Pec requis");
        if (dateSeance == null) throw new IllegalArgumentException("DateSeance requise");
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt requis");
        this.id = id;
        this.centreId = centreId;
        this.patientId = patientId;
        this.pecId = pecId;
        this.dateSeance = dateSeance;
        this.typeSeance = typeSeance;
        this.statut = statut == null ? StatutSeance.CREE : statut;
        this.verrouilleFacturation = verrouilleFacturation;
        this.createdAt = createdAt;
        this.regularisationDeverrouilleeAt = regularisationDeverrouilleeAt;
        this.regularisationDeverrouilleeBy = regularisationDeverrouilleeBy;
        this.forfaitSurcharge = forfaitSurcharge;
    }

    public static Seance creer(SeanceId id, CentreId centreId, PatientId patientId, PecId pecId,
                               LocalDate dateSeance, String typeSeance, OffsetDateTime createdAt) {
        return new Seance(id, centreId, patientId, pecId, dateSeance, typeSeance, StatutSeance.CREE, false, createdAt);
    }

    public void valider() {
        if (verrouilleFacturation) throw new IllegalStateException("SeanceVerrouilleeFacturation");
        if (statut != StatutSeance.CREE) throw new IllegalStateException("SeanceDejaValidee");
        this.statut = StatutSeance.VALIDEE;
    }

    public void signer() {
        if (verrouilleFacturation) throw new IllegalStateException("SeanceVerrouilleeFacturation");
        if (statut != StatutSeance.VALIDEE) throw new IllegalStateException("SeanceDejaSignee");
        this.statut = StatutSeance.SIGNEE;
    }

    public void verrouillerFacturation() {
        this.verrouilleFacturation = true;
    }

    public void declarerForfaitSurcharge(ForfaitApplique forfait, String auteur, OffsetDateTime instant) {
        if (verrouilleFacturation) throw new IllegalStateException("SeanceVerrouilleeFacturation");
        if (forfait == null) throw new IllegalArgumentException("Forfait requis");
        if (instant == null) throw new IllegalArgumentException("Instant requis");
        if (auteur == null || auteur.isBlank()) throw new IllegalArgumentException("Auteur requis");
        // premier auteur/instant conservés si déjà surchargé
        if (this.forfaitSurcharge != null) return;
        this.forfaitSurcharge = new ForfaitApplique(forfait.id(), forfait.code(), forfait.libelle(),
                forfait.prixHt(), auteur, instant);
    }

    public void deverrouillerPourRegularisation(String utilisateurId, LocalDate aujourdhui) {
        if (aujourdhui == null) throw new IllegalArgumentException("aujourdhui requis");
        deverrouillerPourRegularisation(utilisateurId, aujourdhui,
                OffsetDateTime.of(aujourdhui, java.time.LocalTime.NOON, java.time.ZoneOffset.UTC));
    }

    public void deverrouillerPourRegularisation(String utilisateurId, LocalDate aujourdhui, OffsetDateTime maintenant) {
        if (verrouilleFacturation) throw new IllegalStateException("SeanceVerrouilleeFacturation");
        if (statut != StatutSeance.CREE) throw new IllegalStateException("SeanceDejaValidee");
        if (dateSeance == null || aujourdhui == null || !dateSeance.isBefore(aujourdhui)) {
            throw new IllegalArgumentException("ValidationHorsJour");
        }
        if (regularisationDeverrouilleeAt != null) return;
        this.regularisationDeverrouilleeAt = maintenant == null ? OffsetDateTime.of(aujourdhui, java.time.LocalTime.NOON, java.time.ZoneOffset.UTC) : maintenant;
        this.regularisationDeverrouilleeBy = utilisateurId;
    }

    public boolean estDeverrouilleePourRegularisation() {
        return regularisationDeverrouilleeAt != null;
    }

    public SeanceId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public PatientId patientId() {
        return patientId;
    }

    public PecId pecId() {
        return pecId;
    }

    public LocalDate dateSeance() {
        return dateSeance;
    }

    public String typeSeance() {
        return typeSeance;
    }

    public StatutSeance statut() {
        return statut;
    }

    public boolean verrouilleFacturation() {
        return verrouilleFacturation;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime regularisationDeverrouilleeAt() {
        return regularisationDeverrouilleeAt;
    }

    public String regularisationDeverrouilleeBy() {
        return regularisationDeverrouilleeBy;
    }

    public ForfaitApplique forfaitSurcharge() {
        return forfaitSurcharge;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Seance s)) return false;
        return Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}