package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'enregistrement ou du remplacement d'un abord vasculaire (mêmes champs que le legacy). */
public record AbordVasculaireDemande(UUID centerId, String typeAbord, String cote, String localisation, LocalDate dateCreation, LocalDate dateFin,
                                     Boolean actif, String complications) {
}
