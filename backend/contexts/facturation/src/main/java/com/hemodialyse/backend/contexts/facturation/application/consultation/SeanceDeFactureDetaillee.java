package com.hemodialyse.backend.contexts.facturation.application.consultation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Une séance d'une facture avec le forfait de la ligne qui lui est attribuée ; forfait et prix nuls = non attribuable (RG-FAC-048). */
public record SeanceDeFactureDetaillee(UUID seanceId, LocalDate dateSeance, String statut, UUID forfaitId, String forfaitLibelle,
                                       BigDecimal prixUnitaireHt) {
}
