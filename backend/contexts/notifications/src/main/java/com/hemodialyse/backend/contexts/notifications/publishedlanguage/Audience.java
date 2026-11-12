package com.hemodialyse.backend.contexts.notifications.publishedlanguage;

import java.util.List;

/**
 * Ceux qui doivent voir une notification : des rôles du centre (sans préfixe {@code ROLE_}, comparés exactement), éventuellement un
 * destinataire visé (identifiant de compte). Sans rôle : tous les utilisateurs du centre.
 */
public record Audience(List<String> roles, String destinataire) {

    public Audience {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public static Audience tous() {
        return new Audience(List.of(), null);
    }

    public static Audience desRoles(String... roles) {
        return new Audience(List.of(roles), null);
    }

    public static Audience desRolesEtUnDestinataire(List<String> roles, String destinataire) {
        return new Audience(roles, destinataire);
    }
}
