package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.ValidationDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ValidationFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** ACL de la validation (SE2, legacy) : valide par le langage publié {@link ValidationFournie}, puis relit la séance validée. */
public class ValidationParLeLegacy implements ValidationDeSeance {

    private final ValidationFournie validation;
    private final Seances seances;

    public ValidationParLeLegacy(ValidationFournie validation, Seances seances) {
        this.validation = validation;
        this.seances = seances;
    }

    @Override
    public Seance validerParLInfirmier(CentreId centre, SeanceId seance, String auteur) {
        validation.validerParLInfirmier(centre.valeur(), seance.valeur(), auteur);
        return seances.trouver(centre, seance).orElseThrow(SeanceIntrouvable::new);
    }
}
