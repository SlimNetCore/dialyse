package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Implémentation des use cases {@code ObservationBiologiqueUseCases}.
 * Orchestre l'agrégat et le port de sortie ; génère identifiants et instants.
 */
public class ObservationBiologiqueService implements ObservationBiologiqueUseCases {

    private final ObservationBiologiqueRepository observations;
    private final java.util.function.Supplier<UUID> idGenerator;

    public ObservationBiologiqueService(ObservationBiologiqueRepository observations,
                                        java.util.function.Supplier<UUID> idGenerator) {
        this.observations = observations;
        this.idGenerator = idGenerator;
    }

    @Override
    public ObservationBiologiqueId enregistrer(EnregistrerObservationCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        ObservationBiologiqueId id = new ObservationBiologiqueId(idGenerator.get());
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        ObservationBiologique observation = ObservationBiologique.enregistrer(id, commande.patientId(),
                commande.centreId(), commande.code(), commande.libelle(), commande.valeur(),
                commande.unite(), commande.seuilBas(), commande.seuilHaut(), commande.dateObservation(),
                commande.prescripteurId(), commande.laboratoire(), commande.commentaire(), maintenantOd);
        observations.save(observation);
        return id;
    }

    @Override
    public ObservationBiologique consulter(ObservationBiologiqueId observationId, CentreId centreId) {
        return observations.trouver(observationId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Observation biologique introuvable"));
    }

    @Override
    public PagedResult<ObservationBiologique> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        return observations.listerParPatient(centreId, patientId, page, size);
    }
}
