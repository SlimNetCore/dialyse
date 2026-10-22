package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Absence telle que le suivi la lit (modèle de lecture ; l'agrégat {@code AbsencePatient} arrive avec la slice A2).
 * Les montants sont ceux figés à la création : {@code montantTtc} est le <em>montant</em> TTC perdu (colonne
 * {@code prix_ttc}), pas un prix unitaire.
 */
public record AbsenceSuivie(AbsenceId id, PatientId patient, LocalDate dateDeSeance, SourceAbsence source,
                            StatutAbsence statut, MotifAbsence motif, String commentaire, String libelleDuForfait,
                            BigDecimal montantTtc, BigDecimal tauxDeTva, BigDecimal montantHt, Instant declareeLe,
                            Instant qualifieeLe, LocalDate dateDeRattrapage) {

    public AbsenceSuivie {
        if (id == null || patient == null || dateDeSeance == null || source == null || statut == null) {
            throw new IllegalArgumentException("Une absence suivie a une identité, un patient, une date, une source et un statut");
        }
    }

    public boolean enRetard(LocalDate aujourdhui) {
        return DelaiDeQualification.enRetard(statut, dateDeSeance, aujourdhui);
    }
}
