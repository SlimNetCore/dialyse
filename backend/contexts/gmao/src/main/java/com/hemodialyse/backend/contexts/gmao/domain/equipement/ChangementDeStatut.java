package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/**
 * Une ligne de l'historique de statuts (RG-GMA-015) : base du calcul d'indisponibilité. {@code precedent} est nul à
 * l'enregistrement. Le motif est celui que le legacy journalise (KB-GMAO-41) : le cumul des observations pour les commandes
 * de l'administrateur, le motif de l'intervention pour un état constaté.
 */
public record ChangementDeStatut(StatutEquipement precedent, StatutEquipement nouveau, String motif, UtilisateurId par,
                                 Instant le) {

    public ChangementDeStatut {
        if (nouveau == null) throw new IllegalArgumentException("Nouveau statut requis");
    }
}
