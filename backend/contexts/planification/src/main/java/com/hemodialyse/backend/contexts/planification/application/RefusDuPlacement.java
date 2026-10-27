package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;

import java.util.function.Supplier;

/** Traduit les refus du domaine dans le langage publié, que les appelants (la fiche, la sérologie) comprennent. */
final class RefusDuPlacement {

    private RefusDuPlacement() {
    }

    static <T> T traduire(Supplier<T> action) {
        try {
            return action.get();
        } catch (RegleDePlanificationViolee e) {
            throw new PlacementDesPatients.Refus(e.code(), e.getMessage());
        } catch (EcritureConcurrente e) {
            throw new PlacementDesPatients.EcritureConcurrente(e.getMessage());
        }
    }
}
