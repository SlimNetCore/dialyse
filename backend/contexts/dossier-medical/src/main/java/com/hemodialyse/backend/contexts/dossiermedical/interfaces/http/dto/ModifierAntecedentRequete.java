package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;

public record ModifierAntecedentRequete(String diagnosticSystem, String diagnosticCode, String diagnosticDisplay,
                                        String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                                        String statutClinique, String severite, String note) {
}
