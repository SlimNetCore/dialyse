package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.Objects;

/**
 * Le contenu figé d'un instantané : la vue d'ensemble, les indicateurs et les répartitions de la société sur le mois, sérialisés une fois
 * pour toutes (RG-DIR-090). Le domaine ne l'interprète pas : un instantané n'est jamais recalculé ni corrigé.
 */
public record ContenuFige(String texte) {

    public ContenuFige {
        Objects.requireNonNull(texte, "texte");
    }
}
