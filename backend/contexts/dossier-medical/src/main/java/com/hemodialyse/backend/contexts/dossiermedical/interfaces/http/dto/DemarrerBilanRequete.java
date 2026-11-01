package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DemarrerBilanRequete(UUID patientId, UUID centreId, List<EtapeBilanRequete> etapes,
                                   LocalDate dateDemarrage, String commentaire) {
}
