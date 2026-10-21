package com.hemodialyse.backend.infrastructure.acl.facturation;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.DeclarationDesAbsences;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit à la Facturation le langage publié {@link DeclarationDesAbsences} (ACL vers le module Absences, legacy tant qu'il
 * n'est pas un contexte ; retirée avec lui). L'exclusion d'une
 * séance déclare une absence de patient par le service du module, donc avec ses règles (délai de déclaration, date, période déjà
 * facturée, une absence par patient et par jour, {@code AUTRE} avec commentaire) ; un refus est une {@code BusinessException}
 * du module (422 et son code), qui annule la transaction de l'exclusion. La réinclusion retire l'absence déclarée par
 * l'exclusion (ADR 0031) : la ligne est supprimée, faute de quoi la contrainte « une absence par patient et par jour » interdirait
 * d'exclure de nouveau la séance.
 */
@Component
public class AbsencesDesPatientsLegacy implements DeclarationDesAbsences {

    private final AbsencePatientService absences;
    private final JdbcTemplate jdbc;

    public AbsencesDesPatientsLegacy(AbsencePatientService absences, JdbcTemplate jdbc) {
        this.absences = absences;
        this.jdbc = jdbc;
    }

    /**
     * Le motif du module Absences de même nom (table de correspondance : voir le test de l'ACL).
     */
    static MotifAbsence motifDuModuleAbsences(String motif) {
        return MotifAbsence.valueOf(motif);
    }

    @Override
    public UUID declarer(UUID centre, UUID patient, LocalDate dateDeSeance, String motif, String commentaire,
                         UUID auteur) {
        return absences.declarer(centre, patient, dateDeSeance, motifDuModuleAbsences(motif), commentaire, auteur).id();
    }

    @Override
    public void retirer(UUID centre, UUID absence) {
        jdbc.update("DELETE FROM absence_patient WHERE id = ? AND center_id = ?", absence, centre);
    }
}
