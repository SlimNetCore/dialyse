package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'enregistrement d'une observation biologique (mêmes champs que le legacy). */
public record ObservationDemande(UUID centerId, UUID demandeExamenId, String codeSystem, String code, String codeDisplay, BigDecimal valeurNum,
                                 String unite, String valeurTexte, LocalDate datePrelevement, String statut) {
}
