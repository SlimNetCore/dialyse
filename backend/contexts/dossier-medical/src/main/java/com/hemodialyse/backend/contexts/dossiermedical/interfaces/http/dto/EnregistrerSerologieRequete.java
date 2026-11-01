package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnregistrerSerologieRequete(UUID patientId, UUID centreId, String prescripteurId,
                                          LocalDate datePrelevement, String typeSerologie, String resultat,
                                          String valeur, String unite, Instant dateResultat,
                                          String laboratoire, String commentaire) {
}
