package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

/**
 * Tous les champs sont obligatoires (un champ absent est refusé en 400, KB-PLN-02).
 */
public record ReglagesDemande(Boolean replanificationAuto, Integer heuresParVacation, Integer heuresHebdoTempsPlein,
                              Integer reposHebdoMin) {
}
