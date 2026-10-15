package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;

/**
 * Service de domaine : le plan comptable <b>effectif</b> d'un centre, c'est-à-dire ses comptes enregistrés ou, tant qu'il n'a
 * rien enregistré, le plan de départ (RG-CPT-018). Sans état.
 */
public final class PlanDuCentre {

    private final CompteRepository comptes;
    private final UsagesDuParametrage usages;

    public PlanDuCentre(CompteRepository comptes, UsagesDuParametrage usages) {
        this.comptes = comptes;
        this.usages = usages;
    }

    public boolean aEnregistreSesComptes(CentreId centre) {
        return comptes.nombre(centre) > 0;
    }

    public PlanComptable planDeDepart(CentreId centre) {
        return PlanComptable.deDepart(centre, usages.comptesDuParametrage(centre));
    }

    /**
     * Première modification : le plan de départ devient celui, enregistré, du centre.
     */
    public void materialiser(CentreId centre) {
        if (!aEnregistreSesComptes(centre)) {
            comptes.enregistrerTous(planDeDepart(centre).comptes());
        }
    }

    public boolean contientUnCompteActif(CentreId centre, NumeroDeCompte numero) {
        if (aEnregistreSesComptes(centre)) {
            return comptes.trouver(centre, numero).filter(CompteComptable::actif).isPresent();
        }
        return planDeDepart(centre).contientUnCompteActif(numero);
    }

    /**
     * Refuse tout numéro qui n'est pas un compte actif du plan du centre ({@code COMPTE_INCONNU}).
     */
    public void exigerCompteActif(CentreId centre, NumeroDeCompte numero) {
        if (!contientUnCompteActif(centre, numero)) {
            throw new CompteInconnu(numero);
        }
    }
}
