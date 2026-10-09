package com.hemodialyse.backend.application.supervision;

/**
 * Piste d'amélioration pour une requête. Le texte est traduit côté interface à partir du {@code code}
 * ({@code SUPERVISION.CONSEIL.<code>}) ; la {@code valeur} alimente le message (chiffre qui a déclenché la règle).
 *
 * @param code   identifiant stable de la règle
 * @param niveau {@link NiveauConseil#ACTION} : à traiter ; {@link NiveauConseil#INFO} : à connaître
 * @param valeur chiffre ayant déclenché la règle, déjà formaté (peut être vide)
 */
public record Conseil(String code, NiveauConseil niveau, String valeur) {

    public enum NiveauConseil {
        ACTION,
        INFO
    }
}
