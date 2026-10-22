package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee;

/**
 * Retire l'absence que l'exclusion d'une séance de la facturation avait déclarée, quand la séance est réincluse (ADR 0031) : la
 * ligne est supprimée, sinon la contrainte « une absence par patient et par jour » interdirait d'exclure de nouveau la séance.
 * Sans effet si l'absence n'existe plus. Une absence que l'utilisateur a travaillée depuis (requalifiée, rattrapée, annulée) n'est
 * pas supprimée : la réinclusion est refusée avec {@code ABSENCE_D_EXCLUSION_MODIFIEE} (KB-ABS-20).
 */
public class RetirerLAbsenceDeLExclusion {

    private final UniteDeTravail uniteDeTravail;
    private final AbsencesRepository absences;

    public RetirerLAbsenceDeLExclusion(UniteDeTravail uniteDeTravail, AbsencesRepository absences) {
        this.uniteDeTravail = uniteDeTravail;
        this.absences = absences;
    }

    /**
     * @throws RegleDAbsenceViolee {@code ABSENCE_D_EXCLUSION_MODIFIEE}
     */
    public void executer(CentreId centre, AbsenceId id) {
        uniteDeTravail.executer(() -> {
            absences.verrouiller(centre, id).ifPresent(a -> {
                a.exigerRetirableParLaReinclusion();
                absences.supprimer(centre, id);
            });
            return null;
        });
    }
}
