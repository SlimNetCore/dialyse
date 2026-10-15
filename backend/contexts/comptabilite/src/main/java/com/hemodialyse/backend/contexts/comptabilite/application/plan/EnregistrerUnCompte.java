package com.hemodialyse.backend.contexts.comptabilite.application.plan;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;

/**
 * Crée un compte ou remplace le libellé et l'état de celui qui porte ce numéro. La première modification enregistre tout le
 * plan de départ comme celui du centre ; un refus annule cet enregistrement (une seule transaction).
 */
public final class EnregistrerUnCompte {

    private final CompteRepository comptes;
    private final PlanDuCentre plan;
    private final UsagesDuParametrage usages;
    private final UniteDeTravail uniteDeTravail;

    public EnregistrerUnCompte(CompteRepository comptes, PlanDuCentre plan, UsagesDuParametrage usages,
                               UniteDeTravail uniteDeTravail) {
        this.comptes = comptes;
        this.plan = plan;
        this.usages = usages;
        this.uniteDeTravail = uniteDeTravail;
    }

    public CompteVue executer(CentreId centre, String numero, String libelle, boolean actif) {
        NumeroDeCompte compteVise = NumeroDeCompte.de(numero);
        return uniteDeTravail.executer(() -> {
            plan.materialiser(centre);
            CompteComptable compte = comptes.trouver(centre, compteVise).orElse(null);
            if (compte == null) {
                compte = CompteComptable.ouvrir(centre, compteVise, libelle, actif, usages);
            } else {
                compte.redefinir(libelle, actif, usages);
            }
            comptes.enregistrer(compte);
            return CompteVue.depuis(compte);
        });
    }
}
