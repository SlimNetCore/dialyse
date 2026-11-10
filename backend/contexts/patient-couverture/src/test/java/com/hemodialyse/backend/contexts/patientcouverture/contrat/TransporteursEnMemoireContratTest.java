package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CarnetDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CategoriesDeTransport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Contrat du carnet de transporteurs sur le double en mémoire (même jeu de tests que les tables réelles). */
class TransporteursEnMemoireContratTest extends TransporteursContrat {

    private final TransporteursEnMemoire memoire = new TransporteursEnMemoire();

    @Override
    protected CarnetDeTransporteurs carnet() {
        return memoire;
    }

    @Override
    protected LectureDesTransporteurs lecture() {
        return memoire;
    }

    @Override
    protected Transporteurs publie() {
        return memoire;
    }

    @Override
    protected CategoriesDeTransport categories() {
        return memoire.categories();
    }

    @Override
    protected UUID categorie(CentreId centre, String libelle) {
        return memoire.categorie(centre.valeur(), libelle);
    }

    @Override
    protected void patientUtilisant(CentreId centreDuPatient, UUID aller, UUID retour) {
        if (aller != null) memoire.utiliser(aller, 1);
        if (retour != null) memoire.utiliser(retour, 1);
    }
}
