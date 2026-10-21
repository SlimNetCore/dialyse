package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un règlement dans l'historique d'une facture : champs historiques ({@code id, factureId, montant, dateReglement, saisiPar,
 * codeReglement}) plus, depuis F4, {@code modeReglement} et {@code referencePaiement}, omis quand ils sont absents (un règlement
 * d'avant F4 garde exactement la forme historique).
 */
public record PaiementReponse(UUID id, UUID factureId, BigDecimal montant, LocalDate dateReglement, String saisiPar,
                              String codeReglement,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String modeReglement,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String referencePaiement) {

    static PaiementReponse depuis(Reglement reglement) {
        return new PaiementReponse(reglement.id(), reglement.facture(), reglement.montant(), reglement.date(),
                reglement.auteur(), reglement.code(), reglement.mode() == null ? null : reglement.mode().name(),
                reglement.reference());
    }
}
