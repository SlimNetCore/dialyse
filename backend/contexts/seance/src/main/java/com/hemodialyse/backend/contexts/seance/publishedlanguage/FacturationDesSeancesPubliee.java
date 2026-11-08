package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0096) : une séance est-elle facturée ? Réponse lue sur le statut de la séance
 * ({@code FACTUREE}, sans tenir compte de la casse), jamais sur le lien vers la facture. Une séance inconnue ou d'un autre centre n'est
 * pas facturée (comportement du legacy, reproduit). Lecture seule.
 */
public interface FacturationDesSeancesPubliee {

    boolean estFacturee(UUID centre, UUID seance);
}
