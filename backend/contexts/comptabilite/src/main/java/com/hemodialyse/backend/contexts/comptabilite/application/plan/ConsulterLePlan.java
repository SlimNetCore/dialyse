package com.hemodialyse.backend.contexts.comptabilite.application.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.PlanComptableEnLecture;

import java.util.UUID;

/**
 * Langage publié : les autres slices de la Comptabilité (mapping, modèles de pièces, écritures) demandent si un numéro est un
 * compte actif du plan du centre. Un numéro qui n'a pas le format d'un compte n'en est pas un.
 */
public final class ConsulterLePlan implements PlanComptableEnLecture {

    private final PlanDuCentre plan;

    public ConsulterLePlan(PlanDuCentre plan) {
        this.plan = plan;
    }

    @Override
    public boolean estUnCompteActif(UUID centre, String numero) {
        NumeroDeCompte compte;
        try {
            compte = NumeroDeCompte.de(numero);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return plan.contientUnCompteActif(new CentreId(centre), compte);
    }
}
