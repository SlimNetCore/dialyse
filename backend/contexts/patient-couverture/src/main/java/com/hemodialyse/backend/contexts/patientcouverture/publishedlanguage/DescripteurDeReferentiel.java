package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;

/**
 * Langage publié (PL11-1, ADR 0157) : la description d'un référentiel administrable dont Patient et couverture est propriétaire
 * (transporteurs, forfaits, caisses, agences, centres payeurs) — champs, alias d'en-têtes, exemples du modèle d'import, clé
 * d'unicité, ordre d'import. La façade d'administration des référentiels la lit pour construire le formulaire et le lecteur de
 * fichiers ; les règles et les données restent dans le contexte.
 *
 * @param slug          segment d'URL ({@code transporteurs}, {@code forfaits}, {@code caisses}, {@code agences}, {@code centres-payeurs})
 * @param libelle       libellé affiché
 * @param ordreDImport  1 = aucun prérequis ; 2 = dépend d'un référentiel d'ordre 1 ; 3 = d'un d'ordre 2
 * @param champs        champs dans l'ordre d'affichage
 * @param cleNaturelle  clés des champs formant l'unicité dans un centre
 */
public record DescripteurDeReferentiel(String slug, String libelle, int ordreDImport, List<Champ> champs,
                                       List<String> cleNaturelle) {

    public DescripteurDeReferentiel {
        champs = List.copyOf(champs);
        cleNaturelle = List.copyOf(cleNaturelle);
    }

    /** Nature d'un champ. */
    public enum Type { TEXTE, TELEPHONE, DECIMAL, ENTIER, ENUMERATION, REFERENCE }

    /**
     * Un champ.
     *
     * @param cle                 identifiant dans le formulaire et le fichier
     * @param libelle             libellé et en-tête de colonne
     * @param type                nature
     * @param obligatoire         obligatoire (après la valeur par défaut)
     * @param longueurMax         longueur maximale d'un texte, 0 = sans objet
     * @param valeursAutorisees   valeurs d'une énumération
     * @param valeurParDefaut     valeur appliquée quand le champ est vide
     * @param referentielCible    slug du référentiel visé (REFERENCE)
     * @param libelleDuReferentielCible libellé du référentiel visé (REFERENCE)
     * @param exemple             exemple du modèle d'import
     * @param alias               autres en-têtes acceptés
     */
    public record Champ(String cle, String libelle, Type type, boolean obligatoire, int longueurMax,
                        List<String> valeursAutorisees, String valeurParDefaut, String referentielCible,
                        String libelleDuReferentielCible, String exemple, List<String> alias) {

        public Champ {
            valeursAutorisees = valeursAutorisees == null ? List.of() : List.copyOf(valeursAutorisees);
            alias = alias == null ? List.of() : List.copyOf(alias);
        }

        static Champ texte(String cle, String libelle, boolean obligatoire, int longueurMax, String exemple, String... alias) {
            return new Champ(cle, libelle, Type.TEXTE, obligatoire, longueurMax, List.of(), null, null, null, exemple, List.of(alias));
        }

        static Champ telephone(String cle, String libelle, boolean obligatoire, int longueurMax, String exemple, String... alias) {
            return new Champ(cle, libelle, Type.TELEPHONE, obligatoire, longueurMax, List.of(), null, null, null, exemple,
                    List.of(alias));
        }

        static Champ decimal(String cle, String libelle, boolean obligatoire, String exemple, String... alias) {
            return new Champ(cle, libelle, Type.DECIMAL, obligatoire, 0, List.of(), null, null, null, exemple, List.of(alias));
        }

        static Champ enumeration(String cle, String libelle, List<String> valeurs, String parDefaut, String... alias) {
            return new Champ(cle, libelle, Type.ENUMERATION, true, 30, valeurs, parDefaut, null, null, parDefaut, List.of(alias));
        }

        static Champ reference(String cle, String libelle, String cible, String libelleCible, String exemple, String... alias) {
            return new Champ(cle, libelle, Type.REFERENCE, true, 0, List.of(), null, cible, libelleCible, exemple, List.of(alias));
        }
    }
}
