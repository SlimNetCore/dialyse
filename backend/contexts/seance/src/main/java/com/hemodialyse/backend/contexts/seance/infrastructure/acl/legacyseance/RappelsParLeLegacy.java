package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.RappelsFournis;

/** ACL du rappel : traduit le rappel du domaine en langage publié {@link RappelsFournis} (identifiants, nombre, dates). */
public class RappelsParLeLegacy implements RappelsDeRegularisation {

    private final RappelsFournis rappels;

    public RappelsParLeLegacy(RappelsFournis rappels) {
        this.rappels = rappels;
    }

    @Override
    public boolean rappeler(RappelDeRegularisation rappel) {
        return rappels.rappeler(rappel.centre().valeur(), rappel.jour(), rappel.seances().total(), rappel.seances().plusAncienne());
    }
}
