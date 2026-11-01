package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DemanderExamenRequete(UUID patientId, UUID centreId, String prescripteurId,
                                    LocalDate dateDemande, String categorie, boolean urgent, String motif,
                                    List<LigneDemandeExamenRequete> lignes) {
}
