package com.hemodialyse.backend.contexts.facturation.application.consultation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Le détail d'une facture émise (RG-FAC-047, RG-FAC-048) : lignes figées, séances avec leur forfait attribué, totaux FIGÉS de la facture
 * (qui font foi), réglé et reste.
 */
public record DetailDeFacture(UUID factureId, String numeroFacture, BigDecimal tauxTva, List<LigneDeFactureDetaillee> lignes,
                              List<SeanceDeFactureDetaillee> seances, Totaux totaux) {

    public DetailDeFacture {
        lignes = List.copyOf(lignes);
        seances = List.copyOf(seances);
    }

    public record Totaux(BigDecimal montantHt, BigDecimal montantTva, BigDecimal montantTtc, BigDecimal montantRegle,
                         BigDecimal reste) {
    }
}
