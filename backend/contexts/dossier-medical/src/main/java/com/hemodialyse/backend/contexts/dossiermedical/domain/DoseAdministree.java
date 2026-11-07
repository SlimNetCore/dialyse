package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/** Dose réellement administrée : strictement positive, avec son unité (au plus 20 caractères). */
public record DoseAdministree(BigDecimal valeur, String unite) {

    public DoseAdministree {
        if (valeur == null || valeur.signum() <= 0) {
            throw new RegleDeGestionViolee("DOSE_ADMINISTREE_INVALIDE", "La dose administrée doit être strictement positive");
        }
        if (unite == null || unite.isBlank()) {
            throw new RegleDeGestionViolee("DOSE_ADMINISTREE_UNITE_REQUISE", "L'unité de la dose est obligatoire");
        }
        if (unite.length() > 20) throw new SaisieInvalide("L'unité de la dose est limitée à 20 caractères");
        if (valeur.precision() - valeur.scale() > 7) throw new SaisieInvalide("La dose est trop grande");
    }
}
