package com.hemodialyse.backend.contexts.notifications.application;

import java.util.UUID;

/** Port sortant : identifiants aléatoires des notifications sans identifiant déterministe. */
public interface Identifiants {
    UUID suivant();
}
