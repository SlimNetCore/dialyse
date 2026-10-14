package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant : persistance de l'agrégat {@link Equipement} (un repository par racine). {@code enregistrer} écrit aussi les
 * changements de statut levés par l'agrégat (R8 : l'historique est écrit avec l'équipement, dans la même transaction).
 * Un équipement supprimé logiquement reste trouvable (KB-GMAO-34) ; l'écrire à nouveau le rétablit. Jamais de suppression.
 */
public interface EquipementRepository {

    void enregistrer(Equipement equipement);

    Optional<Equipement> trouverParId(EquipementId id);

    /** Historique de statuts, du plus ancien au plus récent (base du calcul d'indisponibilité). */
    List<ChangementDeStatut> historiqueDe(EquipementId id);
}
