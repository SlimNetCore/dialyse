package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /equipements/{id}/reformer : contrat inchangé par rapport au legacy ({@code motif}). */
public record ReformerRequete(
        @NotBlank(message = "Motif de réforme requis")
        String motif) {
}
