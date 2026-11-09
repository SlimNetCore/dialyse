package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.Set;
import java.util.UUID;

/**
 * Commande publiée d'Organisation (organisation.v1, O4 lot D, ADR 0110) : définir EXACTEMENT les salles d'isolement d'un centre. Le
 * paramétrage du planning décide quelles salles sont réservées aux patients à risque infectieux ; l'isolement est une propriété de la
 * salle, donc Organisation seule l'écrit.
 *
 * <p>Effet : toutes les salles du centre cessent d'être des salles d'isolement, puis celles du lot le deviennent. Une salle inconnue ou
 * d'un autre centre dans le lot est ignorée sans erreur ; un centre inconnu ne change rien. Aucune transaction propre : l'écriture
 * s'exécute DANS la transaction de l'appelant (propagation par défaut) et est annulée avec elle.
 */
public interface DefinirLesSallesDIsolement {

    void definir(UUID centre, Set<UUID> sallesDIsolement);
}
