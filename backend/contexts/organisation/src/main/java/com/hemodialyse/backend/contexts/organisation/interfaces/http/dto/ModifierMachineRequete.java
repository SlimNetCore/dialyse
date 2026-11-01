package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

/**
 * Modification d'une machine référencée.
 */
public record ModifierMachineRequete(String code, String libelle, Boolean actif) {
}
