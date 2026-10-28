package com.hemodialyse.backend.contexts.facturation.domain.facture;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant (écriture, ACL sur les séances du module Séance) : la liaison séance → facture (RG-FAC-030). Une séance liée
 * passe à l'état facturée et garde l'identité de sa facture.
 */
public interface LiaisonDesSeances {

    /**
     * Lie les séances à la facture. Échoue (422) si l'une d'elles n'est plus une séance validée ou signée non facturée du centre :
     * l'émission entière est alors annulée.
     */
    void lier(CentreId centre, UUID facture, List<UUID> seances);
}
