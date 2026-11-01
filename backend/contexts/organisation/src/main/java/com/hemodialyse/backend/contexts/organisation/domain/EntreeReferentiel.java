package com.hemodialyse.backend.contexts.organisation.domain;

/**
 * Entrée de référentiel en lecture seule (value object) : projection plate d'une table
 * de référence partagée (payeurs, agences, caisses, transporteurs, catégories, forfaits,
 * états patient). Même forme que le legacy ({@code RefItem}) pour une bascule sans friction.
 */
public record EntreeReferentiel(String id, String code, String nom, String adresse, String prenom,
                                String libelle, Boolean gereParLot) {
}
