package com.hemodialyse.backend.contexts.documents.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du moteur de rendu : la version personnalisée EN VIGUEUR d'un modèle de document d'un centre (DOC3, ADR 0163 ; c'était
 * un port fourni par la plateforme depuis DOC2). Le dépôt et le versionnement sont à ce contexte, {@link GererLesGabarits}.
 */
public interface GabaritPersonnaliseDuCentre {

    Optional<Gabarit> actif(UUID centre, UUID modele);

    /** Une version de gabarit (immuable) : son numéro et son contenu JRXML déjà validé. */
    record Gabarit(int version, String contenu) {
    }
}
