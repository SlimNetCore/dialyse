package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Port d'entrée (use cases) de l'agrégat {@code ObservationBiologique} (slice DM6).
 * Cas d'usage : {@code EnregistrerObservation}, {@code ConsulterObservations}.
 */
public interface ObservationBiologiqueUseCases {

    ObservationBiologiqueId enregistrer(EnregistrerObservationCommande commande, Instant maintenant);

    ObservationBiologique consulter(ObservationBiologiqueId observationId, CentreId centreId);

    PagedResult<ObservationBiologique> listerParPatient(CentreId centreId, UUID patientId, int page, int size);

    record EnregistrerObservationCommande(UUID patientId, CentreId centreId, String code, String libelle,
                                          String valeur, String unite, Double seuilBas, Double seuilHaut,
                                          LocalDate dateObservation, String prescripteurId,
                                          String laboratoire, String commentaire) {
    }
}
