package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FactureAComptabiliser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FactureNonComptabilisee(UUID factureId, String numero, LocalDate dateFacturation,
                                      LocalDate finDePeriode, BigDecimal totalTtc) {
    public static FactureNonComptabilisee depuis(FactureAComptabiliser f) {
        return new FactureNonComptabilisee(f.id(), f.numero(), f.dateFacturation(), f.finDePeriode(), f.totalTtc());
    }
}
