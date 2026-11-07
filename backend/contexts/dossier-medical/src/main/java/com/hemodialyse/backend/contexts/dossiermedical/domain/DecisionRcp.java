package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Décision de RCP : n'a de sens qu'au sein du {@link BilanPreGreffe}. Date de réunion et avis obligatoires, date de réunion jamais
 * dans le futur du jour fourni (RG reproduites ; messages et codes du legacy). Reproduit (KB-MED-93, à confirmer) : doublons, revue
 * antérieure à la réunion et compte rendu sans limite acceptés.
 */
public record DecisionRcp(UUID id, LocalDate dateReunion, AvisRcp avis, String compteRendu, LocalDate prochaineDateRevue) {

    public static DecisionRcp enregistrer(UUID id, LocalDate dateReunion, AvisRcp avis, String compteRendu, LocalDate prochaineDateRevue,
                                          LocalDate aujourdhui) {
        if (dateReunion == null || avis == null) {
            throw new RegleDeGestionViolee("DECISION_RCP_CHAMPS_REQUIS",
                    "La date de réunion et l'avis sont obligatoires pour une décision de RCP");
        }
        if (dateReunion.isAfter(aujourdhui)) {
            throw new RegleDeGestionViolee("DECISION_RCP_DATE_FUTURE", "La date de réunion ne peut pas être dans le futur");
        }
        return new DecisionRcp(id, dateReunion, avis, compteRendu, prochaineDateRevue);
    }
}
