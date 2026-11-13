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

    /** La nature d'un champ : un texte, un choix parmi des valeurs autorisées, ou un entier positif. */
    public enum Genre { TEXTE, CHOIX, ENTIER }

    /**
     * Un champ. Pour un {@link Genre#CHOIX}, {@code valeursAutorisees} et {@code valeurParDefaut} (appliquée quand le champ est vide, la
     * colonne est alors facultative à l'import) ; sans objet pour les autres genres.
     */
    public record Champ(String cle, String libelle, boolean obligatoire, int longueurMax, String exemple, List<String> alias,
                        Genre genre, List<String> valeursAutorisees, String valeurParDefaut) {

        public Champ {
            alias = alias == null ? List.of() : List.copyOf(alias);
            valeursAutorisees = valeursAutorisees == null ? List.of() : List.copyOf(valeursAutorisees);
        }

        /** Un champ de texte. */
        public Champ(String cle, String libelle, boolean obligatoire, int longueurMax, String exemple, List<String> alias) {
            this(cle, libelle, obligatoire, longueurMax, exemple, alias, Genre.TEXTE, List.of(), null);
        }
    }
}
