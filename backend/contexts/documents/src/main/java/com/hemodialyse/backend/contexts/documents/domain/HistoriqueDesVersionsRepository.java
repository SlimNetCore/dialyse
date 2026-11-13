package com.hemodialyse.backend.contexts.documents.domain;

import java.util.Optional;
import java.util.UUID;

/** Port sortant du domaine : l'historique des versions d'un modèle (table {@code modele_document_version}, possédée par Documents). */
public interface HistoriqueDesVersionsRepository {

    /** L'historique du modèle du centre (vide si aucune version déposée) ; sans les contenus. */
    HistoriqueDesVersions historique(UUID centre, UUID modele);

    /** Enregistre les changements d'état des versions et le dépôt en attente. */
    void enregistrer(HistoriqueDesVersions historique);

    /** Le contenu d'une version précise. */
    Optional<String> contenuDe(UUID centre, UUID modele, int numero);

    /** Le contenu de la version en vigueur ; vide si le gabarit livré est utilisé. */
    Optional<String> contenuEnVigueur(UUID centre, UUID modele);

    /** Retire tout l'historique d'un modèle (suppression du modèle). */
    void retirerTout(UUID modele);
}
