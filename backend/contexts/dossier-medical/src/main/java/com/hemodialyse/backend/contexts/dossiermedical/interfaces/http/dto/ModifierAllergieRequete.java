package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;

public record ModifierAllergieRequete(String reaction, LocalDate dateDebut, LocalDate dateFin,
                                      String statutClinique, String severite, String note) {
}
