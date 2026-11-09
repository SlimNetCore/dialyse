package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/**
 * Le libellé métier d'un code d'alerte de la direction, tel qu'il est imprimé dans le rapport de synthèse. Un code inconnu (un instantané
 * figé par une version ancienne ou plus récente) est imprimé tel quel : le rapport ne cache jamais une alerte qu'il ne sait pas nommer.
 */
public final class LibelleDAlerte {

    private LibelleDAlerte() {
    }

    public static String de(String code) {
        return switch (code) {
            case "KTV_CONFORMITE_BASSE" -> "Kt/V dans la cible pour trop peu de patients";
            case "HB_HORS_CIBLE" -> "Hémoglobine dans la cible pour trop peu de patients";
            case "OBSERVANCE_EN_RETARD" -> "Traitement de l'anémie en retard d'administration";
            case "STOCK_SOUS_SEUIL" -> "Articles sous le seuil d'alerte";
            case "LOTS_PERIMES" -> "Lots périmés encore en stock";
            case "LOTS_PEREMPTION_PROCHE" -> "Lots à péremption sous 90 jours";
            default -> code;
        };
    }
}
