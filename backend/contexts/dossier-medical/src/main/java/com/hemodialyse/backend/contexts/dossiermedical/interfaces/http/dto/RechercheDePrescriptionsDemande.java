package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de la recherche de prescriptions : centre et patient obligatoires, page à partir de 0, taille de 1 à 200, période facultative. */
public record RechercheDePrescriptionsDemande(UUID centerId, UUID patientId, Integer page, Integer size, LocalDate dateFrom, LocalDate dateTo) {
}
