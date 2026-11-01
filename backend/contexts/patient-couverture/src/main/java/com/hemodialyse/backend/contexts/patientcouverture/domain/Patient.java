package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

public class Patient {
    private final PatientId id;
    private final CentreId centreId;
    private final Identite identite;
    private final String numeroAssurance;
    private final LocalDate dateAdmission;
    private LocalDate dateSortie;
    private EtatPatient etat;

    public Patient(PatientId id, CentreId centreId, Identite identite, String numeroAssurance,
                   LocalDate dateAdmission, LocalDate dateSortie, EtatPatient etat) {
        if (id == null) throw new IllegalArgumentException("Patient requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (identite == null) throw new IllegalArgumentException("Identite requise");
        if (numeroAssurance == null || numeroAssurance.isBlank())
            throw new IllegalArgumentException("Numero assurance requis");
        if (dateAdmission == null) throw new IllegalArgumentException("Date admission requise");
        this.id = id;
        this.centreId = centreId;
        this.identite = identite;
        this.numeroAssurance = numeroAssurance;
        this.dateAdmission = dateAdmission;
        this.dateSortie = dateSortie;
        this.etat = etat == null ? EtatPatient.ACTIF : etat;
        if (this.etat == EtatPatient.SORTI && this.dateSortie == null) {
            throw new IllegalArgumentException("DateSortieRequise");
        }
    }

    public static Patient admettre(PatientId id, CentreId centreId, Identite identite, String numeroAssurance, LocalDate dateAdmission) {
        return new Patient(id, centreId, identite, numeroAssurance, dateAdmission, null, EtatPatient.ACTIF);
    }

    public PatientId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public Identite identite() {
        return identite;
    }

    public String numeroAssurance() {
        return numeroAssurance;
    }

    public LocalDate dateAdmission() {
        return dateAdmission;
    }

    public LocalDate dateSortie() {
        return dateSortie;
    }

    public EtatPatient etat() {
        return etat;
    }

    public void transferer(CentreId nouveauCentre) {
        if (nouveauCentre == null) throw new IllegalArgumentException("Centre cible requis");
        if (nouveauCentre.equals(centreId)) throw new IllegalArgumentException("TransfertVersMemeCentre");
    }

    public void declarerSortie(LocalDate dateSortie) {
        if (dateSortie == null) throw new IllegalArgumentException("DateSortieRequise");
        if (etat == EtatPatient.SORTI) throw new IllegalArgumentException("PatientDejaSorti");
        this.dateSortie = dateSortie;
        this.etat = EtatPatient.SORTI;
    }

    public void declarerDeces(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("DateDecesRequise");
        this.dateSortie = date;
        this.etat = EtatPatient.DECEDE;
    }

    public void declarerGreffe(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("DateGreffeRequise");
        this.etat = EtatPatient.GREFFE;
    }

    public void declarerGuerison(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("DateGuerisonRequise");
        this.etat = EtatPatient.GUERI;
    }
}