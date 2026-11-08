package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Facturation, côté FOURNI (ADR 0097, ARGENT) : le rattachement des séances à leur facture, fourni par la plateforme à
 * partir de {@code seance.v1}. Si une séance n'est plus éligible, lève {@link IllegalStateException} « Une ou plusieurs seances ne sont plus
 * eligibles a la validation » : la transaction d'émission, UNIQUE et non scindée, est annulée en entier. Participe à la transaction de l'appelant.
 */
public interface LiaisonDesSeancesFournie {

    void lier(UUID centre, UUID facture, List<UUID> seances);
}
