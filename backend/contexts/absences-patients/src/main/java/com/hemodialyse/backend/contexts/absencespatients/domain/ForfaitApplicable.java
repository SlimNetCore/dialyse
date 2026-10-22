package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;

import java.math.BigDecimal;

/**
 * Forfait de la prise en charge du patient retenu pour valoriser une absence (prix hors taxe), choisi par le contexte Patient
 * et couverture (KB-ABS-16).
 */
public record ForfaitApplicable(ForfaitId forfait, String libelle, BigDecimal prixHt) {
    public ForfaitApplicable {
        if (forfait == null) throw new IllegalArgumentException("Forfait requis");
        if (prixHt == null) throw new IllegalArgumentException("Prix du forfait requis");
    }
}
