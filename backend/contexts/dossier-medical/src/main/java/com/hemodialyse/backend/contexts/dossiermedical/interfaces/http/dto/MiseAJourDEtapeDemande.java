package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDEtape;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de la mise à jour d'une étape (mêmes champs que le legacy ; un champ absent est effacé). Statut absent ou inconnu : 400. */
public record MiseAJourDEtapeDemande(UUID centerId, String statut, LocalDate dateRealisation, String resultat, LocalDate dateExpiration,
                                     UUID demandeExamenId, UUID serologieId) {
    public StatutDEtape statutLu() {
        return Enumerations.obligatoire(StatutDEtape.class, statut, "statut");
    }
}
