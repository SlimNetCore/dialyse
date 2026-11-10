package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.application.UsageDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agences;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Caisses;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentresPayeurs;

/** Contrat de la chaîne des payeurs sur le double en mémoire (même jeu de tests que les tables réelles). */
class PayeursEnMemoireContratTest extends PayeursContrat {

    private final PayeursEnMemoire memoire = new PayeursEnMemoire();

    @Override
    protected Caisses caisses() {
        return memoire.caisses();
    }

    @Override
    protected Agences agences() {
        return memoire.agences();
    }

    @Override
    protected CentresPayeurs centresPayeurs() {
        return memoire.centresPayeurs();
    }

    @Override
    protected LectureDesPayeurs lecture() {
        return memoire;
    }

    @Override
    protected UsageDesPayeurs usage() {
        return memoire;
    }

    @Override
    protected com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre publie() {
        return memoire;
    }
}
