package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;

/**
 * Les référentiels administrables de l'Organisation (PL11-2, ADR 0158) : mêmes champs, alias, exemples, libellés et ordre d'import
 * qu'avant leur retrait de l'administration générique (le modèle d'import téléchargé ne change pas d'un octet).
 */
public final class DescripteursDeLOrganisation {

    public static final DescripteurDeReferentiel CRENEAU = new DescripteurDeReferentiel("creneaux", "Créneaux", 1,
            List.of(new DescripteurDeReferentiel.Champ("code", "Code", true, 50, "CR1", List.of("code creneau", "code position")),
                    new DescripteurDeReferentiel.Champ("libelle", "Libellé", true, 255, "Matin (06h30 – 10h30)",
                            List.of("horaire", "plage horaire", "creneau"))),
            List.of("code"));

    private DescripteursDeLOrganisation() {
    }
}
