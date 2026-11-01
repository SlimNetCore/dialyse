package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlerteDirectionGeneree;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Implémentation des use cases {@code AlerteDirectionUseCases}.
 * Orchestre l'agrégat et le port de sortie ; génère identifiants et instants.
 * Garantit R2 (alerte unique par centre et jour) et publie
 * {@code AlerteDirectionGeneree} à la génération.
 */
public class AlerteDirectionService implements AlerteDirectionUseCases {

    private final AlerteDirectionRepository alertes;
    private final java.util.function.Supplier<UUID> idGenerator;
    private final java.util.function.Consumer<AlerteDirectionGeneree> evenementGeneration;

    public AlerteDirectionService(AlerteDirectionRepository alertes,
                                  java.util.function.Supplier<UUID> idGenerator,
                                  java.util.function.Consumer<AlerteDirectionGeneree> evenementGeneration) {
        this.alertes = alertes;
        this.idGenerator = idGenerator;
        this.evenementGeneration = evenementGeneration;
    }

    @Override
    public AlerteDirectionId generer(GenererAlerteCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        if (alertes.existeParCentreEtJour(commande.centreId(), commande.date())) {
            throw new IllegalArgumentException("ALERTE_DEJA_EXISTANTE_POUR_JOUR");
        }
        AlerteDirectionId id = new AlerteDirectionId(idGenerator.get());
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        AlerteDirection alerte = AlerteDirection.generer(id, commande.centreId(), commande.type(),
                commande.seuil(), commande.message(), commande.date(), maintenantOd);
        alertes.save(alerte);
        evenementGeneration.accept(new AlerteDirectionGeneree(
                UUID.randomUUID(), alerte.id().value(), alerte.centreId().valeur(),
                alerte.type().name(), alerte.date(), maintenant));
        return id;
    }

    @Override
    public AlerteDirection acquitter(AlerteDirectionId alerteId, CentreId centreId, Instant maintenant) {
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        AlerteDirection alerte = consulter(alerteId, centreId);
        AlerteDirection acquittee = alerte.acquitter(maintenant.atOffset(ZoneOffset.UTC));
        alertes.save(acquittee);
        return acquittee;
    }

    @Override
    public AlerteDirection consulter(AlerteDirectionId alerteId, CentreId centreId) {
        return alertes.trouver(alerteId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Alerte direction introuvable"));
    }

    @Override
    public PagedResult<AlerteDirection> listerParCentre(CentreId centreId, int page, int size) {
        return alertes.listerParCentre(centreId, page, size);
    }
}
