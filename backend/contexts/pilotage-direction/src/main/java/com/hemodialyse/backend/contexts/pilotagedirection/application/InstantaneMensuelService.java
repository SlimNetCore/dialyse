package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantaneMensuelCapture;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Implémentation des use cases {@code InstantaneMensuelUseCases}.
 * Orchestre l'agrégat et le port de sortie ; génère identifiants et instants.
 * Garantit R1 (un instantané par centre et mois) et publie
 * {@code InstantaneMensuelCapture} à la capture.
 */
public class InstantaneMensuelService implements InstantaneMensuelUseCases {

    private final InstantaneMensuelRepository instantanes;
    private final java.util.function.Supplier<UUID> idGenerator;
    private final java.util.function.Consumer<InstantaneMensuelCapture> evenementCapture;

    public InstantaneMensuelService(InstantaneMensuelRepository instantanes,
                                    java.util.function.Supplier<UUID> idGenerator,
                                    java.util.function.Consumer<InstantaneMensuelCapture> evenementCapture) {
        this.instantanes = instantanes;
        this.idGenerator = idGenerator;
        this.evenementCapture = evenementCapture;
    }

    @Override
    public InstantaneMensuelId capturer(CapturerInstantaneCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        if (instantanes.existeParCentreEtMois(commande.centreId(), commande.mois())) {
            throw new IllegalArgumentException("INSTANTANE_DEJA_EXISTANT");
        }
        InstantaneMensuelId id = new InstantaneMensuelId(idGenerator.get());
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        InstantaneMensuel instantane = InstantaneMensuel.capturer(id, commande.centreId(),
                commande.mois(), commande.donnees(), maintenantOd);
        instantanes.save(instantane);
        evenementCapture.accept(new InstantaneMensuelCapture(
                UUID.randomUUID(), instantane.id().value(), instantane.centreId().valeur(),
                instantane.mois().valeur().getYear(), instantane.mois().valeur().getMonthValue(),
                maintenant));
        return id;
    }

    @Override
    public InstantaneMensuel consulter(InstantaneMensuelId instantaneId, CentreId centreId) {
        return instantanes.trouver(instantaneId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Instantané mensuel introuvable"));
    }

    @Override
    public PagedResult<InstantaneMensuel> listerParCentre(CentreId centreId, int page, int size) {
        return instantanes.listerParCentre(centreId, page, size);
    }
}
