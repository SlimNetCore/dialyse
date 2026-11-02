package com.hemodialyse.backend.contexts.organisation.application;

import java.util.UUID;

/** Port sortant : fournit les identifiants des nouvelles entités (déterministe dans les tests, aléatoire en production). */
@FunctionalInterface
public interface GenerateurDIdentifiants {

    UUID suivant();
}
