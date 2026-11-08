package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0097) : le rattachement des séances à leur facture. ARGENT : c'est le SEUL écrivain
 * de {@code facture_id} hors de la Séance elle-même (SE6). Chaque séance validée ou signée, non facturée, du centre passe à {@code FACTUREE}
 * et porte l'identité de la facture. Si une seule séance n'est plus éligible (aucune ligne modifiée), lève {@link IllegalStateException}
 * « Une ou plusieurs seances ne sont plus eligibles a la validation » : l'appelant annule alors sa transaction ENTIÈRE. N'ouvre aucune
 * transaction (une seule transaction d'émission, ADR 0097 décision 2). Aucune clé étrangère ne protège {@code facture_id} (KB-SEA-74).
 */
public interface LiaisonDesSeancesAUneFacturePubliee {

    void lier(UUID centre, UUID facture, List<UUID> seances);
}
