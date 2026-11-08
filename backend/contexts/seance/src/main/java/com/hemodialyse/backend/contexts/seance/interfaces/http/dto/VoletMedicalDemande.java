package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import com.hemodialyse.backend.contexts.seance.domain.ObservationsMedicales;

import java.util.UUID;

/**
 * Corps de {@code PUT /api/v1/seances/{id}/volet-medical} : mêmes champs JSON que le legacy. Les six observations REMPLACENT le volet en
 * entier : une observation absente efface la valeur déjà saisie (KB-SEA-59, reproduit). Aucun plafond de taille (reproduit). Les champs
 * inconnus sont ignorés. Donnée de santé : jamais citée dans un message d'erreur.
 */
public record VoletMedicalDemande(UUID centerId, String prescription, String toleranceSeance, String examenClinique,
                                  String resultatsBiologiques, String ajustementsTherapeutiques, String conclusionMedicale) {

    public ObservationsMedicales observations() {
        return new ObservationsMedicales(prescription, toleranceSeance, examenClinique, resultatsBiologiques, ajustementsTherapeutiques,
                conclusionMedicale);
    }
}
