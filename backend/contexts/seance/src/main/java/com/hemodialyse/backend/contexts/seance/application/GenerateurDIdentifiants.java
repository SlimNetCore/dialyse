package com.hemodialyse.backend.contexts.seance.application;

import java.util.UUID;

/** Port sortant : identifiants des nouvelles séances (le domaine ne tire jamais d'aléa). */
public interface GenerateurDIdentifiants {
    UUID suivant();
}
