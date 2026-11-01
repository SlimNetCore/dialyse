package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EnregistrerObservationRequete(UUID patientId, UUID centreId, String code, String libelle,
                                            String valeur, String unite, Double seuilBas, Double seuilHaut,
                                            LocalDate dateObservation, String prescripteurId,
                                            String laboratoire, String commentaire) {
}
