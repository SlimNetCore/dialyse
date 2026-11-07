package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de la recherche d'analyses : patient et période seulement (aucun autre critère : reproduit, KB-MED-83). */
public record RechercheDAnalysesDemande(UUID centerId, UUID patientId, Integer page, Integer size, LocalDate dateFrom, LocalDate dateTo) {
}
