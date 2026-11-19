package com.hemodialyse.backend.contexts.facturation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.facturation.application.consultation.FactureListee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Une facture de la liste ({@code GET /api/v1/facturation/factures}, RG-FAC-046). */
public record FactureListeeReponse(UUID factureId, String numeroFacture, LocalDate dateFacturation, LocalDate periodeDebut,
                                   LocalDate periodeFin, UUID patientId, String numeroAssurance, String patientNom,
                                   String patientPrenom, UUID caisseId, String caisse, long nombreDeSeances, BigDecimal tauxTva,
                                   BigDecimal montantHt, BigDecimal montantTva, BigDecimal montantTtc, BigDecimal montantRegle,
                                   BigDecimal reste, BigDecimal tropPercu, String etat) {

    public static FactureListeeReponse depuis(FactureListee f) {
        return new FactureListeeReponse(f.factureId(), f.numeroFacture(), f.dateFacturation(), f.periodeDebut(), f.periodeFin(),
                f.patientId(), f.numeroAssurance(), f.patientNom(), f.patientPrenom(), f.caisseId(), f.caisse(), f.nombreDeSeances(),
                f.tauxTva(), f.montantHt(), f.montantTva(), f.montantTtc(), f.montantRegle(), f.reste(), f.tropPercu(),
                f.etat().name());
    }
}
