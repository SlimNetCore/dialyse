package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeBilan;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port d'entrée (use cases) de l'agrégat {@code BilanPreGreffe} (slice DM7).
 * Cas d'usage : {@code DemarrerBilan}, {@code ValiderEtape}, {@code CompleterBilan}.
 * L'événement {@code BilanPreGreffeComplete} est publié par la couche application
 * à la complétion.
 */
public interface BilanPreGreffeUseCases {

    BilanPreGreffeId demarrer(DemarrerBilanCommande commande, Instant maintenant);

    BilanPreGreffe validerEtape(BilanPreGreffeId bilanId, CentreId centreId, String codeEtape,
                                String validePar, Instant maintenant);

    BilanPreGreffe completer(BilanPreGreffeId bilanId, CentreId centreId, Instant maintenant);

    BilanPreGreffe annuler(BilanPreGreffeId bilanId, CentreId centreId, Instant maintenant);

    BilanPreGreffe consulter(BilanPreGreffeId bilanId, CentreId centreId);

    PagedResult<BilanPreGreffe> listerParPatient(CentreId centreId, UUID patientId, int page, int size);

    record DemarrerBilanCommande(UUID patientId, CentreId centreId, List<EtapeBilan> etapes,
                                 LocalDate dateDemarrage, String commentaire) {
    }
}
