package com.hemodialyse.backend.contexts.documents.domain;

import java.util.UUID;

/** Le modèle de document d'un centre, tel que l'historique des versions le désigne : code (nom du fichier téléchargé), type et gabarit livré. */
public record ModeleDuCentre(UUID id, UUID centre, String code, String type, String cheminDuGabarit) {
}
