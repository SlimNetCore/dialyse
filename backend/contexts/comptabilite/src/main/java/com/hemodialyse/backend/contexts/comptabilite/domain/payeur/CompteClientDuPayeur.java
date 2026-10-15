package com.hemodialyse.backend.contexts.comptabilite.domain.payeur;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;

/**
 * Compte client propre à un payeur du centre (RG-CPT-019). Sans compte propre, le compte client par défaut du centre
 * s'applique : « retirer » le compte, c'est supprimer cette ligne. Le compte doit être un compte actif du plan.
 */
public final class CompteClientDuPayeur {

    private final CentreId centre;
    private final PayeurId payeur;
    private final NumeroDeCompte compte;

    private CompteClientDuPayeur(CentreId centre, PayeurId payeur, NumeroDeCompte compte) {
        this.centre = centre;
        this.payeur = payeur;
        this.compte = compte;
    }

    /**
     * Affecte un compte au payeur ; refusé ({@code COMPTE_INCONNU}) si ce n'est pas un compte actif du plan du centre.
     */
    public static CompteClientDuPayeur affecter(CentreId centre, PayeurId payeur, NumeroDeCompte compte, PlanDuCentre plan) {
        plan.exigerCompteActif(centre, compte);
        return new CompteClientDuPayeur(centre, payeur, compte);
    }

    public CentreId centre() {
        return centre;
    }

    public PayeurId payeur() {
        return payeur;
    }

    public NumeroDeCompte compte() {
        return compte;
    }
}
