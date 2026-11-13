package com.hemodialyse.backend.contexts.documents.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.VersionsDeModele;

import java.util.UUID;

/** Adapter entrant du langage publié {@link VersionsDeModele} : délègue au cas d'usage de personnalisation des gabarits. */
public class VersionsDeModelePublie implements VersionsDeModele {

    private final GererLesGabarits gabarits;

    public VersionsDeModelePublie(GererLesGabarits gabarits) {
        this.gabarits = gabarits;
    }

    @Override
    public void retirerToutes(UUID centre, UUID modele) {
        gabarits.retirerLHistorique(centre, modele);
    }
}
