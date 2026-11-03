package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Synthèse de l'effectif d'un mois : total, sexe, âge, KT (contrat JSON actuel). */
public record ResumeDesPatients(long totalPatients, List<Tranche> sexDistribution, List<Tranche> ageDistribution,
                                List<Tranche> ktDistribution) {

    public record Tranche(String code, String label, long count) {
    }

    /** Détail du mois : les patients de l'effectif, triés par nom, prénom puis code. */
    public record Detail(String month, long total, List<LigneDuDetail> items) {
    }

    public record LigneDuDetail(UUID patientId, String codePatient, String nom, String prenom, String sexe, String etatPatient,
                                LocalDate dateEvenementEtat, LocalDate dateAdmission, boolean sousKt, String inclusionReason) {
    }
}
