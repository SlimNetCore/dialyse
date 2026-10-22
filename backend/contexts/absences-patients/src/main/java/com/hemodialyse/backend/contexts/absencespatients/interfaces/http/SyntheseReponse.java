package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.absencespatients.domain.SyntheseDesAbsences;

/**
 * Synthèse du suivi : noms JSON inchangés.
 */
public record SyntheseReponse(long aQualifier, long enRetard) {

    static SyntheseReponse depuis(SyntheseDesAbsences synthese) {
        return new SyntheseReponse(synthese.aQualifier(), synthese.enRetard());
    }
}
