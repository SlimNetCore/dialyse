package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;

/** Contrat du catalogue de forfaits sur le double en mémoire (même jeu de tests que la table réelle). */
class ForfaitsEnMemoireContratTest extends ForfaitsContrat {

    private final ForfaitsEnMemoire memoire = new ForfaitsEnMemoire();

    @Override
    protected CatalogueDeForfaits catalogue() {
        return memoire;
    }

    @Override
    protected LectureDuCatalogueDeForfaits lecture() {
        return memoire;
    }

    @Override
    protected ForfaitsDuCentre publie() {
        return memoire;
    }
}
