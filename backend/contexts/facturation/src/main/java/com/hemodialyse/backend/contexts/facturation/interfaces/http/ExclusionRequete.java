package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.simulation.DemandeDeSimulation;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ExclureUneSeance;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /facturation/preview/seances/{id}/exclude} : les champs du legacy
 * ({@code FacturationPreviewExcludeSeanceRequest}) plus {@code motif} (obligatoire : un motif d'absence, ADR 0031) et
 * {@code commentaire} (obligatoire avec le motif {@code AUTRE}). L'auteur de l'absence est l'utilisateur de la session.
 */
public record ExclusionRequete(
        @NotNull UUID centerId,
        @NotBlank String userId,
        @NotBlank String motif,
        String commentaire,
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean regroupementMultiForfait) {

    DemandeDeSimulation demande() {
        return SimulationRequete.demande(month, periodStart, periodEnd, regroupementMultiForfait);
    }

    ExclureUneSeance.Commande commande(UUID auteur) {
        return new ExclureUneSeance.Commande(MotifDAbsence.depuis(motif), commentaire, auteur);
    }
}
