package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.Collection;

/**
 * Le profil qui lit les ordonnances (nom du patient, numéro d'assurance, prescripteur) : un compte qui porte le rôle MEDECIN ou le rôle
 * ADMIN, même s'il en porte d'autres. Même règle que la lecture ({@code GET /ordonnances}) et que l'impression générique du type
 * {@code ORDONNANCE} (KB-MED-18 corrigé, ADR 0081, à confirmer avec les métiers).
 */
public final class ProfilDeLectureDesOrdonnances {

    private ProfilDeLectureDesOrdonnances() {
    }

    /** @param roles les autorités de la session (préfixe {@code ROLE_}) */
    public static boolean autorise(Collection<String> roles) {
        return roles.contains("ROLE_MEDECIN") || roles.contains("ROLE_ADMIN");
    }
}
