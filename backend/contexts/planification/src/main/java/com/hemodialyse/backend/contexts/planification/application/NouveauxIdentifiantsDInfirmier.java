package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

/**
 * Port sortant : attribue l'identité d'une nouvelle fiche (générée par le système, jamais par le client).
 */
public interface NouveauxIdentifiantsDInfirmier {
    InfirmierId suivant();
}
