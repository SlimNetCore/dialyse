package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Corps de la mise à jour du bilan immunologique (mêmes champs que le legacy ; un champ absent est effacé). */
public record BilanImmunologiqueDemande(UUID centerId, String groupeSanguinConfirme, String typageHla, BigDecimal praClasseI,
                                        BigDecimal praClasseII) {
}
