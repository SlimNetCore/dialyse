package com.hemodialyse.backend.contexts.comptabilite.application.plan;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteIntrouvable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;

/**
 * Supprime physiquement un compte : introuvable, puis utilisé, puis porteur d'écritures (dans cet ordre de priorité).
 */
public final class SupprimerUnCompte {

    private final CompteRepository comptes;
    private final PlanDuCentre plan;
    private final UsagesDuParametrage usages;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUnCompte(CompteRepository comptes, PlanDuCentre plan, UsagesDuParametrage usages,
                             UniteDeTravail uniteDeTravail) {
        this.comptes = comptes;
        this.plan = plan;
        this.usages = usages;
        this.uniteDeTravail = uniteDeTravail;
    }

    public void executer(CentreId centre, String numero) {
        NumeroDeCompte compteVise = NumeroDeCompte.de(numero);
        uniteDeTravail.executer(() -> {
            plan.materialiser(centre);
            CompteComptable compte = comptes.trouver(centre, compteVise).orElseThrow(() -> new CompteIntrouvable(compteVise));
            compte.exigerSuppressible(usages);
            comptes.supprimer(centre, compteVise);
            return null;
        });
    }
}
