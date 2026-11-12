package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps des recherches de statistiques d'un patient ({@code POST stats/paramedical/search}, {@code stats/medical/search},
 * {@code stats/export/search}) : centre et patient obligatoires, periode facultative. {@code type} est ignore (reproduit du legacy) ;
 * {@code format} ne sert qu'a l'export.
 */
public record RechercheDeStatistiquesDemande(UUID centerId, UUID patientId, LocalDate dateFrom, LocalDate dateTo, String type, String format) {
}
