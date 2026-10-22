package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;

/**
 * Celui qui écrit : l'utilisateur de la session (nul si la session ne le donne pas) et son droit de correction, calculé à
 * l'interface (administrateur et médecin).
 */
public record Intervenant(UtilisateurId utilisateur, DroitDeCorrection droit) {
    public Intervenant {
        if (droit == null) throw new IllegalArgumentException("Droit de correction requis");
    }
}
