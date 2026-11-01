package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EnregistrerAntecedentRequete(UUID patientId, UUID centreId, String type,
                                           String diagnosticSystem, String diagnosticCode, String diagnosticDisplay,
                                           String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                                           String severite, String note) {
}
