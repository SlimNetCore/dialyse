package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'enregistrement ou de la correction d'un antécédent (mêmes champs que le legacy ; le type est ignoré à la correction). */
public record AntecedentDemande(UUID centerId, String type, String codeSystem, String code, String codeDisplay, String libelleLibre,
                                LocalDate dateDebut, LocalDate dateFin, String statutClinique, String severite, String note) {
}
