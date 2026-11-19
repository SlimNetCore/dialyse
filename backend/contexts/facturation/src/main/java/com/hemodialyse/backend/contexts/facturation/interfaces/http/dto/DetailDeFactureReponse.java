package com.hemodialyse.backend.contexts.facturation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.facturation.application.consultation.DetailDeFacture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Le détail d'une facture ({@code GET /api/v1/facturation/factures/{id}/lignes}, RG-FAC-047, 048). */
public record DetailDeFactureReponse(UUID factureId, String numeroFacture, BigDecimal tauxTva, List<Ligne> lignes,
                                     List<Seance> seances, Totaux totaux) {

    public record Ligne(UUID forfaitId, String forfaitLibelle, BigDecimal prixUnitaireHt, int nombreDeSeances, BigDecimal montantHt,
                        BigDecimal montantTva, BigDecimal montantTtc) {
    }

    /** Forfait, libellé et prix nuls : forfait non attribuable (RG-FAC-048). */
    public record Seance(UUID seanceId, LocalDate dateSeance, String statut, UUID forfaitId, String forfaitLibelle,
                         BigDecimal prixUnitaireHt) {
    }

    public record Totaux(BigDecimal montantHt, BigDecimal montantTva, BigDecimal montantTtc, BigDecimal montantRegle,
                         BigDecimal reste) {
    }

    public static DetailDeFactureReponse depuis(DetailDeFacture d) {
        return new DetailDeFactureReponse(d.factureId(), d.numeroFacture(), d.tauxTva(),
                d.lignes().stream().map(l -> new Ligne(l.forfaitId(), l.forfaitLibelle(), l.prixUnitaireHt(), l.nombreDeSeances(),
                        l.montantHt(), l.montantTva(), l.montantTtc())).toList(),
                d.seances().stream().map(s -> new Seance(s.seanceId(), s.dateSeance(), s.statut(), s.forfaitId(), s.forfaitLibelle(),
                        s.prixUnitaireHt())).toList(),
                new Totaux(d.totaux().montantHt(), d.totaux().montantTva(), d.totaux().montantTtc(), d.totaux().montantRegle(),
                        d.totaux().reste()));
    }
}
