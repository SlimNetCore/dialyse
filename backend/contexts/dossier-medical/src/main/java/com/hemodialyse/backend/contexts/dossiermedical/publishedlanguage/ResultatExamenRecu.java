package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * \u00c9v\u00e9nement de domaine publi\u00e9 : un r\u00e9sultat d'examen a \u00e9t\u00e9 re\u00e7u pour une demande.
 * Produit par la couche application apr\u00e8s ex\u00e9cution de {@code RecevoirResultat}.
 */
public record ResultatExamenRecu(UUID eventId, UUID demandeExamenId, UUID patientId, CentreId centreId,
                                 String categorie, Instant dateReception) {
}
