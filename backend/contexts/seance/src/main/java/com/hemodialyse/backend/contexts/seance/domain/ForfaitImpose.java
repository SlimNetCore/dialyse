package com.hemodialyse.backend.contexts.seance.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Le forfait imposé à une séance : instantané du forfait du référentiel (code, nom, prix) copié au moment de l'imposition, avec l'instant
 * et l'auteur. Il prime sur le forfait de la prise en charge. Aucun historique : une nouvelle imposition remplace tout (KB-SEA-45,
 * reproduit). L'instantané n'est jamais rafraîchi quand le référentiel change. Restitué sans contrôle : une ligne écrite par la
 * Facturation peut n'avoir ni code, ni instant, ni auteur.
 */
public record ForfaitImpose(ForfaitId forfait, String code, String nom, BigDecimal prix, Instant imposeLe, String auteur) {
    public ForfaitImpose {
        if (forfait == null) throw new SaisieInvalide("Forfait requis");
    }
}
