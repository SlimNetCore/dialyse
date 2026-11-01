package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementPatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementPatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementType;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.util.UUID;

public class PatientService implements PatientUseCases {
    private final PatientRepository patients;
    private final MouvementPatientRepository mouvements;
    private final java.util.function.Supplier<UUID> idGenerator;

    public PatientService(PatientRepository patients, MouvementPatientRepository mouvements,
                          java.util.function.Supplier<UUID> idGenerator) {
        this.patients = patients;
        this.mouvements = mouvements;
        this.idGenerator = idGenerator;
    }

    @Override
    public PatientId admettre(AdmettrePatientCommande commande, Instant maintenant) {
        PatientId id = new PatientId(idGenerator.get());
        Patient p = Patient.admettre(id, commande.centreId(), commande.identite(), commande.numeroAssurance(), commande.dateAdmission());
        patients.sauvegarder(p);
        mouvements.sauvegarder(new MouvementPatient(new MouvementPatientId(idGenerator.get()), id, commande.centreId(),
                MouvementType.ADMISSION, commande.dateAdmission(), null, commande.auteurId(), maintenant));
        return id;
    }

    @Override
    public void transferer(TransfererPatientCommande commande, Instant maintenant) {
        Patient p = patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        p.transferer(commande.nouveauCentre());
        patients.sauvegarder(p);
        mouvements.sauvegarder(new MouvementPatient(new MouvementPatientId(idGenerator.get()), commande.patientId(),
                commande.centreId(), MouvementType.TRANSFERT, maintenant.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                commande.motif(), commande.auteurId(), maintenant));
    }

    @Override
    public void declarerSortie(SortirPatientCommande commande, Instant maintenant) {
        Patient p = patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        p.declarerSortie(commande.dateSortie());
        patients.sauvegarder(p);
        mouvements.sauvegarder(new MouvementPatient(new MouvementPatientId(idGenerator.get()), commande.patientId(),
                commande.centreId(), MouvementType.SORTIE, commande.dateSortie(), null, commande.auteurId(), maintenant));
    }

    @Override
    public void declarerDeces(DeclarerDecesCommande commande, Instant maintenant) {
        Patient p = patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        p.declarerDeces(commande.date());
        patients.sauvegarder(p);
        mouvements.sauvegarder(new MouvementPatient(new MouvementPatientId(idGenerator.get()), commande.patientId(),
                commande.centreId(), MouvementType.DECES, commande.date(), null, commande.auteurId(), maintenant));
    }

    @Override
    public void declarerGreffe(DeclarerGreffeCommande commande, Instant maintenant) {
        Patient p = patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        p.declarerGreffe(commande.date());
        patients.sauvegarder(p);
        mouvements.sauvegarder(new MouvementPatient(new MouvementPatientId(idGenerator.get()), commande.patientId(),
                commande.centreId(), MouvementType.GREFFE, commande.date(), null, commande.auteurId(), maintenant));
    }

    @Override
    public void declarerGuerison(DeclarerGuerisonCommande commande, Instant maintenant) {
        Patient p = patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        p.declarerGuerison(commande.date());
        patients.sauvegarder(p);
        mouvements.sauvegarder(new MouvementPatient(new MouvementPatientId(idGenerator.get()), commande.patientId(),
                commande.centreId(), MouvementType.GUERISON, commande.date(), null, commande.auteurId(), maintenant));
    }

    @Override
    public Patient consulter(PatientId patientId, CentreId centreId) {
        return patients.trouver(patientId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
    }

    @Override
    public PagedResult<Patient> lister(CentreId centreId, int page, int size) {
        return patients.lister(centreId, page, size);
    }
}