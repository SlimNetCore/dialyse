package com.hemodialyse.backend.contexts.documents.domain;

import java.util.UUID;

/**
 * Le modèle de document d'un centre pour un type : gabarit et format d'impression (lecture de {@code modele_document}, que Documents
 * possède). {@code cheminDuGabarit} est vide et {@code formatDImpression} vaut PDF quand la base ne les renseigne pas ; {@code type} vaut
 * CUSTOM sans type en base.
 */
public record ModeleDeDocument(UUID id, String cheminDuGabarit, String formatDImpression, String type) {
}
