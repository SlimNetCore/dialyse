package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de la déclaration d'une allergie (mêmes champs que le legacy). */
public record AllergieDemande(UUID centerId, String codeSystem, String code, String codeDisplay, String categorie, String criticite,
                              String typeReaction, String manifestations, LocalDate dateConstatation, String statutVerification) {
}
