package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroitId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AttestationService implements AttestationUseCases {
    private final AttestationRepository attestations;
    private final PatientRepository patients;
    private final java.util.function.Supplier<UUID> idGenerator;

    public AttestationService(AttestationRepository attestations, PatientRepository patients, java.util.function.Supplier<UUID> idGenerator) {
        this.attestations = attestations;
        this.patients = patients;
        this.idGenerator = idGenerator;
    }

    @Override
    public AttestationDroitId enregistrer(EnregistrerAttestationCommande commande, Instant maintenant) {
        patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        AttestationDroitId id = new AttestationDroitId(idGenerator.get());
        AttestationDroit a = AttestationDroit.creer(id, commande.patientId(), commande.centreId(),
                commande.dateDebut(), commande.dateFin(), java.time.OffsetDateTime.now());
        attestations.sauvegarder(a);
        return id;
    }

    @Override
    public AttestationDroit consulter(AttestationDroitId id, CentreId centreId) {
        return attestations.trouver(id, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Attestation introuvable"));
    }

    @Override
    public PagedResult<AttestationDroit> lister(CentreId centreId, int page, int size) {
        return attestations.lister(centreId, page, size);
    }

    @Override
    public List<AttestationDroit> listerParPatient(PatientId patientId, CentreId centreId) {
        return attestations.listerParPatient(patientId, centreId);
    }

    @Override
    public void supprimer(AttestationDroitId id, CentreId centreId) {
        attestations.supprimer(id, centreId);
    }
}