package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeRepository;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.BilanPreGreffeComplete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Implémentation des use cases {@code BilanPreGreffeUseCases}.
 * Orchestre l'agrégat et le port de sortie ; génère identifiants et instants.
 * Publie {@code BilanPreGreffeComplete} à la complétion.
 */
public class BilanPreGreffeService implements BilanPreGreffeUseCases {

    private final BilanPreGreffeRepository bilans;
    private final java.util.function.Supplier<UUID> idGenerator;
    private final java.util.function.Consumer<BilanPreGreffeComplete> evenementComplete;

    public BilanPreGreffeService(BilanPreGreffeRepository bilans,
                                 java.util.function.Supplier<UUID> idGenerator,
                                 java.util.function.Consumer<BilanPreGreffeComplete> evenementComplete) {
        this.bilans = bilans;
        this.idGenerator = idGenerator;
        this.evenementComplete = evenementComplete;
    }

    @Override
    public BilanPreGreffeId demarrer(DemarrerBilanCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        BilanPreGreffeId id = new BilanPreGreffeId(idGenerator.get());
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        BilanPreGreffe bilan = BilanPreGreffe.demarrer(id, commande.patientId(), commande.centreId(),
                commande.etapes(), commande.dateDemarrage(), commande.commentaire(), maintenantOd);
        bilans.save(bilan);
        return id;
    }

    @Override
    public BilanPreGreffe validerEtape(BilanPreGreffeId bilanId, CentreId centreId, String codeEtape,
                                       String validePar, Instant maintenant) {
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        BilanPreGreffe bilan = consulter(bilanId, centreId);
        BilanPreGreffe modifie = bilan.validerEtape(codeEtape, validePar, maintenant.atOffset(ZoneOffset.UTC));
        bilans.save(modifie);
        return modifie;
    }

    @Override
    public BilanPreGreffe completer(BilanPreGreffeId bilanId, CentreId centreId, Instant maintenant) {
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        BilanPreGreffe bilan = consulter(bilanId, centreId);
        BilanPreGreffe complete = bilan.completer(maintenant.atOffset(ZoneOffset.UTC));
        bilans.save(complete);
        evenementComplete.accept(new BilanPreGreffeComplete(
                UUID.randomUUID(), complete.id().value(), complete.patientId(),
                complete.centreId().valeur(), maintenant));
        return complete;
    }

    @Override
    public BilanPreGreffe annuler(BilanPreGreffeId bilanId, CentreId centreId, Instant maintenant) {
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        BilanPreGreffe bilan = consulter(bilanId, centreId);
        BilanPreGreffe annule = bilan.annuler(maintenant.atOffset(ZoneOffset.UTC));
        bilans.save(annule);
        return annule;
    }

    @Override
    public BilanPreGreffe consulter(BilanPreGreffeId bilanId, CentreId centreId) {
        return bilans.trouver(bilanId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Bilan pré-greffe introuvable"));
    }

    @Override
    public PagedResult<BilanPreGreffe> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        return bilans.listerParPatient(centreId, patientId, page, size);
    }
}
