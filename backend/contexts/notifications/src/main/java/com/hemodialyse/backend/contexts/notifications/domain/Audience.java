package com.hemodialyse.backend.contexts.notifications.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ceux qui doivent voir une notification : un ou plusieurs rôles du centre (sans préfixe {@code ROLE_}, comparés EXACTEMENT : le souligne
 * n'est pas un joker, KB-NOT1-1), et éventuellement un destinataire visé (compte précis, pour les notifications personnelles). Sans rôle,
 * la notification est visible de tous ; le destinataire visé la voit en plus des rôles listés.
 */
public record Audience(List<String> roles, String destinataire) {

    public Audience {
        roles = List.copyOf(new LinkedHashSet<>(propres(roles)));
        destinataire = destinataire == null || destinataire.isBlank() ? null : destinataire.trim();
    }

    private static List<String> propres(Collection<String> roles) {
        List<String> propres = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) propres.add(role.trim());
            }
        }
        return propres;
    }

    public static Audience desRoles(Collection<String> roles) {
        return new Audience(List.copyOf(propres(roles)), null);
    }

    public static Audience desRolesEtUnDestinataire(Collection<String> roles, String destinataire) {
        return new Audience(List.copyOf(propres(roles)), destinataire);
    }

    public boolean estOuverteATous() {
        return roles.isEmpty();
    }

    /** Vrai si l'utilisateur ayant ces rôles (sans préfixe) et cet identifiant de compte doit voir la notification. */
    public boolean inclut(Set<String> rolesDeLUtilisateur, String utilisateur) {
        if (estOuverteATous()) return true;
        if (destinataire != null && destinataire.equals(utilisateur)) return true;
        for (String role : roles) {
            if (rolesDeLUtilisateur.contains(role)) return true;
        }
        return false;
    }

    public String rolesEnTexte() {
        return String.join(",", roles);
    }
}
