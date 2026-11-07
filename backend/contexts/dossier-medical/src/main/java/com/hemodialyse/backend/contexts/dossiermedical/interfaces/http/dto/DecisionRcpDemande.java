package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AvisRcp;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'ajout d'une décision de RCP (mêmes champs que le legacy). Avis absent ou inconnu : 400 (avant : 500 ou nom de classe). */
public record DecisionRcpDemande(UUID centerId, LocalDate dateReunion, String avis, String compteRendu, LocalDate prochaineDateRevue) {
    public AvisRcp avisLu() {
        return Enumerations.obligatoire(AvisRcp.class, avis, "avis");
    }
}
