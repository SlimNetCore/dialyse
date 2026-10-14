package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Corps de POST /plans-maintenance : contrat inchangé par rapport au legacy (noms de champs et contraintes). */
public record EtablirPlanRequete(
        @NotNull(message = "ID équipement requis")
        UUID equipementId,

        @NotBlank(message = "Désignation requise")
        @Size(max = 255, message = "Désignation limitée à 255 caractères")
        String designation,

        String description,

        @NotBlank(message = "Fréquence requise")
        String frequence,

        @NotNull(message = "Prochaine date prévue requise")
        OffsetDateTime prochaineDatePrevue,

        String tachesAEffectuer) {
}
