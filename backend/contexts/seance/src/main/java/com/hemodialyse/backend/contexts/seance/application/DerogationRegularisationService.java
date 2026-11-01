package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.DerogationId;
import com.hemodialyse.backend.contexts.seance.domain.DerogationRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.DerogationRegularisationRepository;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

public class DerogationRegularisationService implements DerogationRegularisationUseCases {
    private final DerogationRegularisationRepository repository;
    private final SeanceRepository seances;
    private final Supplier<UUID> idGenerator;

    public DerogationRegularisationService(DerogationRegularisationRepository repository,
                                           SeanceRepository seances,
                                           Supplier<UUID> idGenerator) {
        this.repository = repository;
        this.seances = seances;
        this.idGenerator = idGenerator;
    }

    @Override
    public DerogationId creer(CreerDerogationCommande commande) {
        Seance s = seances.trouver(commande.seanceId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (s.verrouilleFacturation()) throw new IllegalStateException("SeanceVerrouilleeFacturation");
        if (s.statut() != com.hemodialyse.backend.contexts.seance.domain.StatutSeance.CREE) {
            throw new IllegalStateException("SeanceDejaValidee");
        }
        DerogationId id = new DerogationId(idGenerator.get());
        DerogationRegularisation d = DerogationRegularisation.declarer(id, commande.centreId(),
                commande.seanceId(), commande.motif(), commande.autorite(),
                OffsetDateTime.now(java.time.ZoneOffset.UTC));
        repository.sauvegarder(d);
        return id;
    }

    @Override
    public DerogationRegularisation consulter(DerogationId id, CentreId centreId) {
        return repository.trouver(id, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Derogation introuvable"));
    }

    @Override
    public DerogationRegularisation consulterParSeance(SeanceId seanceId, CentreId centreId) {
        return repository.trouverParSeance(seanceId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Derogation introuvable"));
    }
}