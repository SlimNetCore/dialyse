package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Les six observations du médecin sur une séance (prescription, tolérance de la séance, examen clinique, résultats biologiques,
 * ajustements thérapeutiques, conclusion médicale). Chacune est facultative ; une observation absente est ABSENTE, jamais « inchangée »
 * (KB-SEA-59, reproduit : le volet est remplacé en entier). AUCUN plafond de taille : les colonnes sont de type {@code text} sans borne
 * (Flyway V1) et le legacy accepte tout (KB-SEA-59, reproduit, risque signalé dans l'ADR 0094 : à décider par les médecins / le DPO).
 * Donnée de santé : jamais citée dans un message d'erreur.
 */
public record ObservationsMedicales(String prescription, String toleranceDeLaSeance, String examenClinique, String resultatsBiologiques,
                                    String ajustementsTherapeutiques, String conclusionMedicale) {
}
