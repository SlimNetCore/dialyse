package com.hemodialyse.backend.domain.facturation.aggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Agrégat TVA : type de TVA applicable à une prestation pour une période donnée.
 * Partagé entre le module facturation et le module comptabilité via les ports.
 * La règle active à la date de facturation est figée sur la facture (immutabilité historique).
 */
public record TypeTVA(
        UUID id,
        UUID centerId,
        String libelle,
        BigDecimal taux,
        String typePrestation,
        boolean exonere,
        LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        String texteReference,
        boolean actif,
        OffsetDateTime createdAt,
        String createdBy
) {
    public TypeTVA {
        if (id == null) throw new IllegalArgumentException("L'identifiant du type TVA est obligatoire");
        if (centerId == null) throw new IllegalArgumentException("Le centre est obligatoire");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Le libellé est obligatoire");
        if (taux == null || taux.signum() < 0)
            throw new IllegalArgumentException("Le taux TVA ne peut pas être négatif");
        if (taux.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Le taux TVA ne peut pas dépasser 100%");
        if (typePrestation == null || typePrestation.isBlank())
            throw new IllegalArgumentException("Le type de prestation est obligatoire");
        if (dateDebutValidite == null)
            throw new IllegalArgumentException("La date de début de validité est obligatoire");
    }

    /**
     * Factory — création d'un nouveau type TVA.
     */
    public static TypeTVA creer(UUID centerId, String libelle, BigDecimal taux, String typePrestation,
                                boolean exonere, LocalDate dateDebutValidite, LocalDate dateFinValidite,
                                String texteReference, String createdBy) {
        return new TypeTVA(
                UUID.randomUUID(), centerId, libelle, taux, typePrestation, exonere,
                dateDebutValidite, dateFinValidite, texteReference, true, OffsetDateTime.now(), createdBy
        );
    }

    /**
     * Retourne true si ce type TVA est actif à la date donnée.
     */
    public boolean isActiveAt(LocalDate date) {
        if (!actif) return false;
        if (date.isBefore(dateDebutValidite)) return false;
        return dateFinValidite == null || !date.isAfter(dateFinValidite);
    }

    /**
     * Ratio (taux / 100) pour les calculs de montant.
     */
    public BigDecimal tauxRatio() {
        return taux.movePointLeft(2).setScale(6, RoundingMode.HALF_UP);
    }
}

