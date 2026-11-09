package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import com.hemodialyse.backend.contexts.seance.domain.MesuresDeSeance;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corps de {@code PUT /api/v1/seances/{seanceId}/volet-paramedical} : mêmes noms de champs et mêmes types que le legacy
 * ({@code UpsertVoletParamedicalRequest}). Un champ absent efface la mesure (remplacement total). Les mesures sont lues APRÈS les
 * contrôles du centre, de la séance et de son statut ({@link #mesures()} est passé en {@code Supplier}).
 */
public record VoletParamedicalDemande(UUID centerId, BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String taAvant, String taApres,
                                      Integer dureeMinutes, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, String anticoagulant,
                                      String typeDialysat, String incidents) {

    public MesuresDeSeance mesures() {
        return MesuresDeSeance.saisies(poidsAvantKg, poidsApresKg, taAvant, taApres, dureeMinutes, debitSangMlMin, ultrafiltrationMl,
                anticoagulant, typeDialysat, incidents);
    }
}
