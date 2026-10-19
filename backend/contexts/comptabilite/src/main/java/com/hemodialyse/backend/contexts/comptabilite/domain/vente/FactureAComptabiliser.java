package com.hemodialyse.backend.contexts.comptabilite.domain.vente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Photo d'une facture lue chez la Facturation, telle que la Comptabilité la comptabilise (slice C5).
 *
 * @param payeur       le payeur de la facture ; nul si le patient paie lui-même
 * @param finDePeriode dernier jour de la période facturée (date d'écriture de la vente)
 */
public record FactureAComptabiliser(UUID id, String numero, UUID payeur, BigDecimal totalHt, BigDecimal totalTva,
                                    BigDecimal totalTtc, LocalDate dateFacturation, LocalDate finDePeriode,
                                    String libelle) {

    public FactureAComptabiliser {
        if (id == null) throw new IllegalArgumentException("L'identifiant de la facture est obligatoire");
    }
}

