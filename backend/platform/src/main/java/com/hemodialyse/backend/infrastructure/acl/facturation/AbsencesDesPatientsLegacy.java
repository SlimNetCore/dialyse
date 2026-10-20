package com.hemodialyse.backend.infrastructure.acl.facturation;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.contexts.facturation.application.simulation.AbsencesDesPatients;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ACL de la Facturation vers le module Absences (legacy tant qu'il n'est pas un contexte ; retirée avec lui). L'exclusion d'une
 * séance déclare une absence de patient par le service du module, donc avec ses règles (délai de déclaration, date, période déjà
 * facturée, une absence par patient et par jour, {@code AUTRE} avec commentaire) ; un refus est une {@code BusinessException}
 * du module (422 et son code), qui annule la transaction de l'exclusion. La réinclusion retire l'absence déclarée par
 * l'exclusion (ADR 0031) : la ligne est supprimée, faute de quoi la contrainte « une absence par patient et par jour » interdirait
 * d'exclure de nouveau la séance.
 */
@Component
public class AbsencesDesPatientsLegacy implements AbsencesDesPatients {

    private final AbsencePatientService absences;
    private final JdbcTemplate jdbc;

    public AbsencesDesPatientsLegacy(AbsencePatientService absences, JdbcTemplate jdbc) {
        this.absences = absences;
        this.jdbc = jdbc;
    }

    /**
     * Le motif du module Absences de même nom (table de correspondance : voir le test de l'ACL).
     */
    static MotifAbsence motifDuModuleAbsences(MotifDAbsence motif) {
        return MotifAbsence.valueOf(motif.name());
    }

    @Override
    public UUID declarer(CentreId centre, UUID patient, LocalDate dateDeSeance, MotifDAbsence motif, String commentaire,
                         UUID auteur) {
        return absences.declarer(centre.valeur(), patient, dateDeSeance, motifDuModuleAbsences(motif), commentaire, auteur).id();
    }

    @Override
    public void retirer(CentreId centre, UUID absence) {
        jdbc.update("DELETE FROM absence_patient WHERE id = ? AND center_id = ?", absence, centre.valeur());
    }
}
