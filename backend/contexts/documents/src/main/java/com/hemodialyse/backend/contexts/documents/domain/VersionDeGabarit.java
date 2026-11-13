package com.hemodialyse.backend.contexts.documents.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Une version de gabarit déposée pour un modèle d'un centre : numéro (à partir de 1), empreinte et taille du fichier, commentaire de
 * dépôt, déposant et date. Le contenu n'en fait pas partie (il se lit à part). Une version est immuable ; seul son état « en vigueur »
 * change, par {@link HistoriqueDesVersions}.
 */
public record VersionDeGabarit(UUID id, int numero, boolean active, String empreinte, int tailleEnOctets, String commentaire,
                               String deposePar, Instant deposeLe) {

    VersionDeGabarit desactivee() {
        return active ? new VersionDeGabarit(id, numero, false, empreinte, tailleEnOctets, commentaire, deposePar, deposeLe) : this;
    }

    VersionDeGabarit activee() {
        return active ? this : new VersionDeGabarit(id, numero, true, empreinte, tailleEnOctets, commentaire, deposePar, deposeLe);
    }
}
