package com.hemodialyse.backend.contexts.documents.domain;

import java.util.UUID;

/** Un modèle de document du centre tel que la liste le présente : toutes ses caractéristiques, actif ou non (lecture de {@code modele_document}). */
public record ModeleDuCatalogue(UUID id, UUID centre, String code, String libelle, String type, String cheminDuGabarit, String format,
                                String description, boolean actif) {
}
