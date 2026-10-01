package com.hemodialyse.backend.domain.gmao.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Intervenant GMAO (technicien interne ou prestataire externe) — référentiel propre au bounded
 * context GMAO (pas de réutilisation de {@code Fournisseur} du stock, AGENTS.md §14). Permet le
 * suivi du coût de maintenance par prestataire (aide à la décision).
 */
public record Intervenant(
        UUID id,
        UUID centreId,
        String nom,
        TypeIntervenant type,
        String telephone,
        String email,
        BigDecimal tarifHoraireDefaut,
        boolean actif
) {
    public static Intervenant creer(UUID centreId, String nom, TypeIntervenant type, String telephone,
                                    String email, BigDecimal tarifHoraireDefaut) {
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        if (type == null) throw new IllegalArgumentException("Type d'intervenant requis");
        if (tarifHoraireDefaut != null && tarifHoraireDefaut.signum() < 0) {
            throw new IllegalArgumentException("Le tarif horaire ne peut pas être négatif");
        }
        return new Intervenant(UUID.randomUUID(), centreId, nom, type, telephone, email, tarifHoraireDefaut, true);
    }

    public Intervenant desactiver() {
        return new Intervenant(id, centreId, nom, type, telephone, email, tarifHoraireDefaut, false);
    }

    public Intervenant modifier(String nom, TypeIntervenant type, String telephone, String email,
                                BigDecimal tarifHoraireDefaut) {
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        if (type == null) throw new IllegalArgumentException("Type d'intervenant requis");
        if (tarifHoraireDefaut != null && tarifHoraireDefaut.signum() < 0) {
            throw new IllegalArgumentException("Le tarif horaire ne peut pas être négatif");
        }
        return new Intervenant(id, centreId, nom, type, telephone, email, tarifHoraireDefaut, actif);
    }
}
