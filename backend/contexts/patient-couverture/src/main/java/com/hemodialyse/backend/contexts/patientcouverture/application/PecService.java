package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Pec;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PecService implements PecUseCases {
    private final PecRepository pecs;
    private final PatientRepository patients;
    private final java.util.function.Supplier<UUID> idGenerator;

    public PecService(PecRepository pecs, PatientRepository patients, java.util.function.Supplier<UUID> idGenerator) {
        this.pecs = pecs;
        this.patients = patients;
        this.idGenerator = idGenerator;
    }

    @Override
    public PecId creer(CreerPecCommande commande, Instant maintenant) {
        patients.trouver(commande.patientId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        PecId id = new PecId(idGenerator.get());
        Pec p = Pec.creer(id, commande.patientId(), commande.centreId(), commande.dateDebutDemande(),
                commande.dateFinDemande(), commande.forfaitDemandeId(), java.time.OffsetDateTime.now());
        pecs.sauvegarder(p);
        return id;
    }

    @Override
    public void valider(ValiderPecCommande commande) {
        Pec p = pecs.trouver(commande.pecId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
        p.valider(commande.dateDebutEffectif(), commande.dateFinEffectif(), commande.forfaitEffectifId());
        pecs.sauvegarder(p);
    }

    @Override
    public void cloturer(CloturerPecCommande commande) {
        Pec p = pecs.trouver(commande.pecId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
        p.cloturer();
        pecs.sauvegarder(p);
    }

    @Override
    public Pec consulter(PecId pecId, CentreId centreId) {
        return pecs.trouver(pecId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("PEC introuvable"));
    }

    @Override
    public PagedResult<Pec> lister(CentreId centreId, int page, int size) {
        return pecs.lister(centreId, page, size);
    }

    @Override
    public List<Pec> listerParPatient(PatientId patientId, CentreId centreId) {
        return pecs.listerParPatient(patientId, centreId);
    }

    @Override
    public boolean autoriseSeance(PecId pecId, CentreId centreId) {
        return pecs.trouver(pecId, centreId).map(Pec::autoriseSeance).orElse(false);
    }

    @Override
    public void supprimer(PecId pecId, CentreId centreId) {
        pecs.supprimer(pecId, centreId);
    }
}