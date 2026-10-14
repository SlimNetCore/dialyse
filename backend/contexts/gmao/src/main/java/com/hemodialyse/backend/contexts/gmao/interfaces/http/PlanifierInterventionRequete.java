package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Corps de POST /interventions : contrat inchangé par rapport au legacy (mêmes champs et mêmes messages de validation).
 * {@code type}, {@code etatEquipementAvant} et {@code priorite} restent des codes texte reconnus par le cas d'usage : un code
 * inconnu répond 400 avec le texte de l'énumération (KB-GMAO-53). Le centre, l'identifiant et le statut du corps sont ignorés.
 */
public record PlanifierInterventionRequete(
        @NotNull(message = "ID équipement requis")
        UUID equipementId,

        @NotBlank(message = "Type d'intervention requis")
        String type,

        @NotNull(message = "Date de début requise")
        OffsetDateTime dateDebut,

        @NotBlank(message = "Description requise")
        String description,

        UUID intervenantId,

        @NotBlank(message = "État de l'équipement requis")
        String etatEquipementAvant,

        /** Panne constatée (symptôme), optionnelle. */
        String symptome,

        /** NORMALE (défaut), HAUTE ou URGENTE. */
        String priorite,

        /** Échéance de réalisation, optionnelle. */
        OffsetDateTime echeance) {
}
