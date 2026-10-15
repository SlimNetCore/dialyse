package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.doubles.PayeursEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;

/**
 * Le double en mémoire passe le contrat partagé des payeurs et de leur compte client.
 */
class PayeurEnMemoireContratTest extends PayeurContrat {

    private final PayeursEnMemoire memoire = new PayeursEnMemoire();

    @Override
    protected CompteClientDuPayeurRepository comptesClients() {
        return memoire;
    }

    @Override
    protected LectureDesPayeurs lecture() {
        return memoire;
    }

    @Override
    protected PayeurId payeur(CentreId centre, String code, String nom) {
        return memoire.ajouterPayeur(centre, code, nom);
    }

    @Override
    protected void poserLeCompteBrut(CentreId centre, PayeurId payeur, String compte) {
        memoire.poserLeCompteBrut(centre, payeur, compte);
    }
}
