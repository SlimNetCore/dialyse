package com.hemodialyse.backend.contexts.absencespatients.infrastructure.identifiants;

import com.hemodialyse.backend.contexts.absencespatients.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;

import java.util.UUID;

/**
 * Identifiants d'absence tirés au hasard (UUID), comme le legacy.
 */
public class GenerateurDIdentifiantsAleatoires implements GenerateurIdentifiants {

    @Override
    public AbsenceId nouvelleAbsence() {
        return new AbsenceId(UUID.randomUUID());
    }
}
