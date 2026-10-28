package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.absences;

import com.hemodialyse.backend.contexts.facturation.application.simulation.AbsencesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.seance.MotifDAbsence;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.DeclarationDesAbsences;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Traduit le port de la Facturation vers le langage publié que le module Absences fournit : identifiants simples et motif par son nom.
 */
public final class AbsencesDesPatientsPubliees implements AbsencesDesPatients {

    private final DeclarationDesAbsences absences;

    public AbsencesDesPatientsPubliees(DeclarationDesAbsences absences) {
        this.absences = absences;
    }

    @Override
    public UUID declarer(CentreId centre, UUID patient, LocalDate dateDeSeance, MotifDAbsence motif, String commentaire,
                         UUID auteur) {
        return absences.declarer(centre.valeur(), patient, dateDeSeance, motif.name(), commentaire, auteur);
    }

    @Override
    public void retirer(CentreId centre, UUID absence) {
        absences.retirer(centre.valeur(), absence);
    }
}
