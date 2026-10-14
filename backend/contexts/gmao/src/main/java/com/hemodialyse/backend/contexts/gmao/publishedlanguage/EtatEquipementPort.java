package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié du contexte GMAO : appliquer à un équipement l'état relevé par une intervention (au démarrage et à la
 * clôture). Utilisé par la clôture d'intervention, encore dans le legacy tant que G4 n'est pas refaite.
 * <p>
 * Contrat (KB-GMAO-40, reproduit) : sans effet si l'équipement est déjà dans cet état ; {@code REFORME} et {@code DESACTIF}
 * ne se fixent jamais ainsi ; un équipement réformé ne change plus ; équipement inconnu ou d'un autre centre refusé.
 * L'équipement, son historique et les événements s'écrivent dans une seule transaction.
 */
public interface EtatEquipementPort {

    /**
     * @param etat code du statut constaté ({@code EN_SERVICE}, {@code EN_MAINTENANCE}, {@code EN_ATTENTE_PIECE},
     *             {@code HORS_SERVICE}, {@code A_REFORMER}...)
     * @throws IllegalArgumentException équipement introuvable dans le centre, ou état non fixable
     * @throws IllegalStateException    équipement réformé
     */
    void constaterEtat(UUID equipementId, UUID centreId, String etat, String motif, UUID parUtilisateur);
}
