package com.hemodialyse.backend.contexts.gmao.application;

import java.util.UUID;

/** Port sortant : fabrique des identifiants uniques pour les nouveaux agrégats. */
public interface GenerateurIdentifiants {
    UUID nouvelIdentifiant();
}
