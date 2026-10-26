package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Nom, prénom et téléphone d'un infirmier. Le nom est obligatoire ; le prénom et le téléphone blancs sont absents. Bornes des
 * colonnes (nom et prénom 255, téléphone 50) refusées à la saisie (KB-PLN-13).
 */
public record IdentiteDeLInfirmier(String nom, String prenom, String telephone) {
    public static final int NOM_MAXIMAL = 255;
    public static final int TELEPHONE_MAXIMAL = 50;

    public IdentiteDeLInfirmier {
        if (nom == null || nom.isBlank()) {
            throw new SaisieInvalide("Nom requis");
        }
        nom = nom.trim();
        prenom = nettoye(prenom);
        telephone = nettoye(telephone);
        borne(nom, NOM_MAXIMAL, "Le nom");
        borne(prenom, NOM_MAXIMAL, "Le prénom");
        borne(telephone, TELEPHONE_MAXIMAL, "Le téléphone");
    }

    private static String nettoye(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static void borne(String valeur, int maximum, String libelle) {
        if (valeur != null && valeur.length() > maximum) {
            throw new SaisieInvalide(libelle + " ne peut pas dépasser " + maximum + " caractères");
        }
    }

    /** « Prénom Nom », ou le nom seul sans prénom. */
    public String nomComplet() {
        return prenom == null ? nom : prenom + " " + nom;
    }
}
