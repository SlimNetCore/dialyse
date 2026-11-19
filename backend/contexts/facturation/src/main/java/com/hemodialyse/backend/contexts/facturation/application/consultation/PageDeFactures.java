package com.hemodialyse.backend.contexts.facturation.application.consultation;

import java.time.LocalDate;
import java.util.List;

/** Une page de la liste des factures, avec la période effective ({@code du}/{@code au}, déduite si absente) et les totaux du filtré. */
public record PageDeFactures(List<FactureListee> items, long total, int page, int taille, LocalDate du, LocalDate au,
                             TotauxDesFactures totaux) {

    public PageDeFactures {
        items = List.copyOf(items);
    }
}
