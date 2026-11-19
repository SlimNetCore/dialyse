package com.hemodialyse.backend.contexts.facturation.application.reglement.pagination;

/**
 * Paramètres d'une liste paginée de la Facturation refusés : 400 avec un code stable, les mêmes que les listes du Stock
 * (contrat {@code .ddd/slices/pagination-serveur/contract.md}) — copie propre au contexte, sans dépendance Stock ↔ Facturation.
 */
public final class PaginationInvalide extends RuntimeException {

    public static final String PAGE_INVALIDE = "PAGE_INVALIDE";
    public static final String TAILLE_INVALIDE = "TAILLE_INVALIDE";
    public static final String TRI_INVALIDE = "TRI_INVALIDE";
    public static final String FILTRE_INVALIDE = "FILTRE_INVALIDE";
    public static final String PARAMETRE_INVALIDE = "PARAMETRE_INVALIDE";

    private final String code;

    public PaginationInvalide(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
