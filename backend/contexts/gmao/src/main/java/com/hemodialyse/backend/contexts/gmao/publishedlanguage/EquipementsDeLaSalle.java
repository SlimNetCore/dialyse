package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié de la GMAO (PL11-4, ADR 0160) : le nombre d'équipements (dont les générateurs) rattachés à une salle, même retirés du
 * parc. L'Organisation s'en sert pour décider entre supprimer et désactiver une salle ; une erreur technique est propagée.
 */
@FunctionalInterface
public interface EquipementsDeLaSalle {

    long nombre(UUID salle);
}
