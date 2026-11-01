package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

/**
 * Élément de référentiel legacy (même forme JSON que l'ancien {@code RefItem} :
 * id, code, nom, adresse, prenom, libelle, gereParLot).
 */
public record RefItemReponse(String id, String code, String nom, String adresse, String prenom,
                             String libelle, Boolean gereParLot) {
}
