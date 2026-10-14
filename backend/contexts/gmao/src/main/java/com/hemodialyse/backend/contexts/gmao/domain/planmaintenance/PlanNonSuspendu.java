package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

/** Règle R26 : la reprise exige un plan suspendu (INACTIF). Message identique au legacy (parité). */
public class PlanNonSuspendu extends IllegalStateException {
    PlanNonSuspendu() {
        super("Seul un plan inactif peut être réactivé");
    }
}
