package com.hemodialyse.backend.domain.comptabilite.valueobject;

import java.time.LocalDate;

/**
 * Règle TVA applicable à une prestation, versionnée dans le temps.
 * La valeur exacte (exonéré ou taux) est une donnée de configuration — jamais une constante.
 * La règle active à la date de la facture est figée sur l'écriture générée (immutabilité historique).
 */
public record RegleTVA(
        String typePrestation,
        java.math.BigDecimal tauxApplique,
        boolean exonere,
        LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        String texteReference
) {
    public RegleTVA {
        if (typePrestation == null || typePrestation.isBlank()) {
            throw new IllegalArgumentException("Le type de prestation est obligatoire");
        }
        if (tauxApplique == null || tauxApplique.signum() < 0) {
            throw new IllegalArgumentException("Le taux TVA ne peut pas être négatif");
        }
        if (dateDebutValidite == null) {
            throw new IllegalArgumentException("La date de début de validité est obligatoire");
        }
    }

    /**
     * Retourne true si la règle est active à la date donnée.
     */
    public boolean isActiveAt(LocalDate date) {
        if (date.isBefore(dateDebutValidite)) return false;
        return dateFinValidite == null || !date.isAfter(dateFinValidite);
    }
}

