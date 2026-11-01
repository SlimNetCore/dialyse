package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Identite;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementPatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface PatientUseCases {
    PatientId admettre(AdmettrePatientCommande commande, Instant maintenant);

    void transferer(TransfererPatientCommande commande, Instant maintenant);

    void declarerSortie(SortirPatientCommande commande, Instant maintenant);

    void declarerDeces(DeclarerDecesCommande commande, Instant maintenant);

    void declarerGreffe(DeclarerGreffeCommande commande, Instant maintenant);

    void declarerGuerison(DeclarerGuerisonCommande commande, Instant maintenant);

    Patient consulter(PatientId patientId, CentreId centreId);

    PagedResult<Patient> lister(CentreId centreId, int page, int size);

    record AdmettrePatientCommande(CentreId centreId, Identite identite, String numeroAssurance,
                                   LocalDate dateAdmission, UUID auteurId) {
    }

    record TransfererPatientCommande(PatientId patientId, CentreId centreId, CentreId nouveauCentre, String motif,
                                     UUID auteurId) {
    }

    record SortirPatientCommande(PatientId patientId, CentreId centreId, LocalDate dateSortie, UUID auteurId) {
    }

    record DeclarerDecesCommande(PatientId patientId, CentreId centreId, LocalDate date, UUID auteurId) {
    }

    record DeclarerGreffeCommande(PatientId patientId, CentreId centreId, LocalDate date, UUID auteurId) {
    }

    record DeclarerGuerisonCommande(PatientId patientId, CentreId centreId, LocalDate date, UUID auteurId) {
    }
}