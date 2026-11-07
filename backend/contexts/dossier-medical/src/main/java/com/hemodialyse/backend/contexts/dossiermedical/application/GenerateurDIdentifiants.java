package com.hemodialyse.backend.contexts.dossiermedical.application;

import java.util.UUID;

/** Port sortant : identifiants des nouvelles fiches (le domaine ne tire jamais d'aléa). */
public interface GenerateurDIdentifiants {
    UUID suivant();
}
