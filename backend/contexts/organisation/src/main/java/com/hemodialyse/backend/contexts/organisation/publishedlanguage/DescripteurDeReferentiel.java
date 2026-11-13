package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;

/**
 * Langage publié {@code organisation.v1} (PL11-2, ADR 0158) : la description d'un référentiel administrable dont l'Organisation est
 * propriétaire — champs, alias d'en-têtes, exemples du modèle d'import, clé d'unicité, ordre d'import. La façade d'administration
 * des référentiels la lit pour construire le formulaire et le lecteur de fichiers ; les règles et les données restent dans le contexte.
 */
public record DescripteurDeReferentiel(String slug, String libelle, int ordreDImport, List<Champ> champs,
                                       List<String> cleNaturelle) {

    public DescripteurDeReferentiel {
        champs = List.copyOf(champs);
        cleNaturelle = List.copyOf(cleNaturelle);
    }

    /** Un champ de texte. */
    public record Champ(String cle, String libelle, boolean obligatoire, int longueurMax, String exemple, List<String> alias) {

        public Champ {
            alias = alias == null ? List.of() : List.copyOf(alias);
        }
    }
}
