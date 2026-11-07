package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'enregistrement d'un résultat sérologique (mêmes champs que le legacy). */
public record SerologieDemande(UUID centerId, String marqueur, String resultat, BigDecimal titre, String unite, LocalDate datePrelevement,
                               String laboratoire, LocalDate dateProchainControle, String conduiteATenir) {
}
