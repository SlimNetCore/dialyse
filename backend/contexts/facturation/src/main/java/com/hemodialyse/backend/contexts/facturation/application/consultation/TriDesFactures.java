package com.hemodialyse.backend.contexts.facturation.application.consultation;

import java.util.Objects;

/** Le tri demandé sur la liste des factures ; le départage reste toujours date décroissante, numéro décroissant, identifiant. */
public record TriDesFactures(ChampDeTri champ, boolean descendant) {

    public TriDesFactures {
        Objects.requireNonNull(champ, "Champ de tri requis");
    }
}
