package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.Optional;
import java.util.UUID;

/**
 * Port FOURNI PAR LA PLATEFORME (transitoire, DOC2) : la version personnalisée ACTIVE d'un modèle de document d'un centre. Le dépôt et
 * le versionnement des gabarits personnalisés restent à la plateforme jusqu'à DOC3 ; le moteur ne fait que les lire.
 */
public interface GabaritPersonnaliseDuCentre {

    Optional<Gabarit> actif(UUID centre, UUID modele);

    /** Une version de gabarit (immuable) : son numéro et son contenu JRXML déjà validé. */
    record Gabarit(int version, String contenu) {
    }
}
