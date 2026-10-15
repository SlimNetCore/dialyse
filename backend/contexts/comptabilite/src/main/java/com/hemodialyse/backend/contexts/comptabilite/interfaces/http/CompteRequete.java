package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /comptes/{numero}}. {@code actif} est un {@link Boolean} : absent ou {@code null}, il est refusé par un
 * 400 explicite (écart décidé KB-CPT-01 ; le legacy répondait 500).
 */
public record CompteRequete(@NotBlank @Size(max = CompteComptable.LIBELLE_MAX) String libelle, Boolean actif) {

    boolean actifObligatoire() {
        if (actif == null) {
            throw new IllegalArgumentException("actif obligatoire");
        }
        return actif;
    }
}
