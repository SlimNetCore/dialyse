package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EnregistrerAllergieRequete(UUID patientId, UUID centreId, String allergene, String type,
                                         String reaction, LocalDate dateDebut, LocalDate dateFin,
                                         String severite, String note) {
}
