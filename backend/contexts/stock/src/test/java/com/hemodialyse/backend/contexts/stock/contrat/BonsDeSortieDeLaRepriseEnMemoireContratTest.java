package com.hemodialyse.backend.contexts.stock.contrat;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.BonsDeSortieDeLaReprise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Le double en mémoire du contrôle des bons de sortie d'un patient repris passe le contrat. */
class BonsDeSortieDeLaRepriseEnMemoireContratTest extends BonsDeSortieDeLaRepriseContrat {

    private record Bon(UUID id, UUID centre, UUID patient) {
    }

    private final List<Bon> bons = new ArrayList<>();

    private final BonsDeSortieDeLaReprise reprise = patient -> bons.stream().filter(b -> Objects.equals(b.patient(), patient))
            .map(b -> b.id().toString()).toList();

    @Override
    protected BonsDeSortieDeLaReprise reprise() {
        return reprise;
    }

    @Override
    protected void bonDeSortie(UUID id, UUID centre, UUID patient) {
        bons.add(new Bon(id, centre, patient));
    }
}
