package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Forfait appliqué à une séance (RG-FAC-021), dans cet ordre : la surcharge posée sur la séance ; la prise en charge qui couvre
 * la date (effective ou demandée) ; à défaut, la prise en charge la plus récente même échue (KB-FAC-18 reproduit) ; sinon le
 * forfait non défini, à 0. Parmi plusieurs prises en charge, celle dotée d'un forfait effectif passe avant, puis la plus récente.
 */
public final class PolitiqueDeForfaitApplique {

    private static final Comparator<PriseEnChargeTarifee> PRIORITE = Comparator
            .comparing((PriseEnChargeTarifee pec) -> !pec.forfaitEffectif())
            .thenComparing(PriseEnChargeTarifee::creeeLe, Comparator.nullsLast(Comparator.<Instant>reverseOrder()));

    private PolitiqueDeForfaitApplique() {
    }

    public static ForfaitApplique pour(LocalDate date, Optional<ForfaitApplique> surcharge, List<PriseEnChargeTarifee> prisesEnCharge) {
        if (surcharge.isPresent()) {
            return surcharge.get();
        }
        return prisesEnCharge.stream().filter(pec -> pec.couvre(date)).min(PRIORITE)
                .or(() -> prisesEnCharge.stream().min(PRIORITE))
                .map(PriseEnChargeTarifee::forfait)
                .orElse(ForfaitApplique.NON_DEFINI);
    }
}
