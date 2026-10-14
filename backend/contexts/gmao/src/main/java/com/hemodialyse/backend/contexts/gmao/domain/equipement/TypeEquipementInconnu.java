package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/**
 * Code de type inconnu. Étend {@link IllegalArgumentException} : le contrat d'erreur observé est HTTP 400 BAD_REQUEST
 * (KB-GMAO-35). Le texte exposé par le legacy (nom de classe interne de l'énumération) est reproduit par l'adapter HTTP.
 */
public class TypeEquipementInconnu extends IllegalArgumentException {
    private final String code;

    public TypeEquipementInconnu(String code) {
        super("Type d'équipement inconnu : " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
