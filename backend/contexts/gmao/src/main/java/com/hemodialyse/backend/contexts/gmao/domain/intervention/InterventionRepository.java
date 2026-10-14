package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import java.util.Optional;

/**
 * Port sortant : persistance de l'agrégat {@link Intervention} (un repository par racine), avec ses lignes de coût et ses
 * rectifications. Une intervention supprimée logiquement reste trouvable ; l'écrire à nouveau la rétablit (KB-GMAO-54).
 * Jamais de suppression.
 */
public interface InterventionRepository {

    void enregistrer(Intervention intervention);

    Optional<Intervention> trouverParId(InterventionId id);

    /**
     * Charge l'intervention pour la modifier : la ligne est verrouillée jusqu'à la fin de la transaction en cours, de sorte
     * que deux commandes simultanées sur la même intervention s'exécutent l'une après l'autre (aucune mise à jour perdue,
     * limite de tâches tenue). À appeler dans la transaction de la commande.
     */
    Optional<Intervention> chargerPourModification(InterventionId id);
}
