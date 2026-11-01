package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PecId;
import com.hemodialyse.backend.contexts.seance.domain.PecValidationPort;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

public class SeanceService implements SeanceUseCases {
    private final SeanceRepository seances;
    private final PecValidationPort pecValidation;
    private final Supplier<UUID> idGenerator;

    public SeanceService(SeanceRepository seances, PecValidationPort pecValidation, Supplier<UUID> idGenerator) {
        this.seances = seances;
        this.pecValidation = pecValidation;
        this.idGenerator = idGenerator;
    }

    @Override
    public SeanceId creer(CreerSeanceCommande commande, Instant maintenant) {
        if (!pecValidation.estValide(commande.pecId(), commande.centreId(), commande.patientId())) {
            throw new IllegalArgumentException("PECInvalide");
        }
        SeanceId id = new SeanceId(idGenerator.get());
        Seance s = Seance.creer(id, commande.centreId(), commande.patientId(), commande.pecId(),
                commande.dateSeance(), commande.typeSeance(), java.time.OffsetDateTime.ofInstant(maintenant, java.time.ZoneOffset.UTC));
        seances.sauvegarder(s);
        return id;
    }

    @Override
    public void valider(ValiderSeanceCommande commande) {
        Seance s = seances.trouver(commande.seanceId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (!s.estDeverrouilleePourRegularisation()
                && s.dateSeance() != null
                && !s.dateSeance().equals(java.time.LocalDate.now(java.time.ZoneOffset.UTC))) {
            throw new IllegalStateException("ValidationHorsJour");
        }
        s.valider();
        seances.sauvegarder(s);
    }

    @Override
    public void signer(SignerSeanceCommande commande) {
        Seance s = seances.trouver(commande.seanceId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        s.signer();
        seances.sauvegarder(s);
    }

    @Override
    public void deverrouiller(DeverrouillerPourRegularisationCommande commande) {
        Seance s = seances.trouver(commande.seanceId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        s.deverrouillerPourRegularisation(null, java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        seances.sauvegarder(s);
    }

    @Override
    public void debloquer(DebloquerPourRegularisationCommande commande) {
        deverrouiller(new DeverrouillerPourRegularisationCommande(commande.seanceId(), commande.centreId()));
    }

    @Override
    public void declarerForfaitSurcharge(DeclarerForfaitSurchargeCommande commande) {
        Seance s = seances.trouver(commande.seanceId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        s.declarerForfaitSurcharge(commande.forfait(), commande.auteur(),
                java.time.OffsetDateTime.ofInstant(commande.instant() == null ? java.time.Instant.now() : commande.instant(),
                        java.time.ZoneOffset.UTC));
        seances.sauvegarder(s);
    }

    @Override
    public Seance consulter(SeanceId seanceId, CentreId centreId) {
        return seances.trouver(seanceId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
    }

    @Override
    public PagedResult<Seance> lister(CentreId centreId, int page, int size) {
        return seances.lister(centreId, page, size);
    }

    @Override
    public PagedResult<Seance> listerDuJour(CentreId centreId, LocalDate date, int page, int size) {
        return seances.listerDuJour(centreId, date, page, size);
    }
}