package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.util.Collection;

/**
 * Règle unique du profil autorisé à imprimer la synthèse mensuelle de facturation, sur les deux voies
 * ({@code POST /facturation/synthese/print} et {@code POST /documents/print[/{modeleId}]} pour ce type) : un compte qui porte
 * le rôle ADMIN ou le rôle SECRETAIRE, même s'il en porte d'autres. Un médecin, un infirmier, ou un compte MEDECIN et
 * INFIRMIER sans ADMIN ni SECRETAIRE est refusé (403).
 */
public final class ProfilDeLaSynthese {

    /** Même règle, sous la forme attendue par {@code @PreAuthorize}. */
    public static final String EXPRESSION = "hasAnyRole('ADMIN','SECRETAIRE')";

    private ProfilDeLaSynthese() {
    }

    /** @param roles les autorités de la session (préfixe {@code ROLE_}) */
    public static boolean autorise(Collection<String> roles) {
        return roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SECRETAIRE");
    }
}
