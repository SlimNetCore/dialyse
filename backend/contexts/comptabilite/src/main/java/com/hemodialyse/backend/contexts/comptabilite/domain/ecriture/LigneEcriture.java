package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Une ligne d'écriture : un compte, un montant au débit ou au crédit (jamais négatif, arrondi au centime « au plus proche »,
 * HALF_UP), un libellé, le tiers lettré et les axes analytiques. Immuable.
 *
 * @param compte le numéro du compte comptable, tel qu'il est écrit (sans espaces de bord)
 * @param tiers  le tiers à lettrer (payeur, patient) ; nul si la ligne n'en a pas
 */
public record LigneEcriture(UUID id, String compte, String libelle, BigDecimal debit, BigDecimal credit, UUID tiers,
                            List<AxeAnalytique> axes) {

    public LigneEcriture {
        if (id == null) throw new IllegalArgumentException("L'identifiant de ligne est obligatoire");
        if (compte == null || compte.isBlank()) throw new IllegalArgumentException("Le compte SCF est obligatoire");
        if (debit == null || debit.signum() < 0)
            throw new IllegalArgumentException("Le débit ne peut pas être négatif");
        if (credit == null || credit.signum() < 0)
            throw new IllegalArgumentException("Le crédit ne peut pas être négatif");
        compte = compte.trim();
        libelle = libelle != null ? libelle.trim() : "";
        debit = debit.setScale(2, RoundingMode.HALF_UP);
        credit = credit.setScale(2, RoundingMode.HALF_UP);
        axes = axes != null ? List.copyOf(axes) : List.of();
    }
}
