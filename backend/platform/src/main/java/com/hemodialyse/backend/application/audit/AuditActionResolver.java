package com.hemodialyse.backend.application.audit;

import java.util.Locale;
import java.util.Set;

/**
 * Décide, à partir de la méthode HTTP et du gabarit de route (ex. {@code /api/v1/patients/{id}}), si une requête
 * doit être tracée et, si oui, sous quel libellé. Classe pure (aucune dépendance Spring ni JPA — AGENTS.md §3).
 * <p>
 * <b>Écritures</b> : toute requête {@code POST}/{@code PUT}/{@code PATCH}/{@code DELETE} sur {@code /api/v1/**} est
 * tracée automatiquement, quelle que soit la ressource — c'est le filet de sécurité qui garantit une couverture
 * complète, y compris pour du code futur non explicitement instrumenté.
 * <p>
 * <b>Lectures</b> : volontairement non tracées par défaut (trop volumineux, peu utile) sauf pour la liste explicite
 * {@link #SENSITIVE_READS} — aujourd'hui la consultation du dossier médical d'un patient.
 * <p>
 * Le libellé produit reste générique (« Modification : patients/{id} ») ; des libellés métier plus fins peuvent être
 * ajoutés progressivement, endpoint par endpoint, sans remettre en cause ce filet de sécurité générique.
 */
public final class AuditActionResolver {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Gabarits de route (tels que renvoyés par Spring MVC) dont la simple lecture doit être tracée.
     */
    private static final Set<String> SENSITIVE_READS = Set.of(
            "/api/v1/patients/{patientId}/dossier-medical"
    );

    private AuditActionResolver() {
    }

    /**
     * @param routeTemplate gabarit de route résolu par Spring MVC (jamais l'URL brute : évite l'explosion de
     *                      cardinalité et les identifiants nominatifs dans le libellé)
     */
    public static boolean isTracked(String method, String routeTemplate) {
        if (routeTemplate == null || method == null) return false;
        if (WRITE_METHODS.contains(method)) return true;
        return "GET".equals(method) && SENSITIVE_READS.contains(routeTemplate);
    }

    public static Resolved resolve(String method, String routeTemplate) {
        String verb = verb(method);
        String resource = resource(routeTemplate);
        String actionCode = (resource + "_" + verb).toUpperCase(Locale.ROOT).replace('-', '_');
        String libelle = verbLabel(verb) + " : " + routeTemplate.replaceFirst("^/api/v1/", "");
        return new Resolved(actionCode, resource, libelle);
    }

    private static String verb(String method) {
        return switch (method) {
            case "POST" -> "CREATION";
            case "PUT", "PATCH" -> "MODIFICATION";
            case "DELETE" -> "SUPPRESSION";
            default -> "CONSULTATION";
        };
    }

    private static String verbLabel(String verb) {
        return switch (verb) {
            case "CREATION" -> "Création";
            case "MODIFICATION" -> "Modification";
            case "SUPPRESSION" -> "Suppression";
            default -> "Consultation";
        };
    }

    /**
     * Premier segment significatif après {@code /api/v1/} (ex. {@code patients}, {@code stock}) — sauf pour le
     * dossier médical, distingué explicitement pour rester lisible dans le journal.
     */
    private static String resource(String routeTemplate) {
        if (routeTemplate.contains("/dossier-medical")) return "dossier-medical";
        String tail = routeTemplate.replaceFirst("^/api/v1/", "");
        int slash = tail.indexOf('/');
        String first = slash < 0 ? tail : tail.substring(0, slash);
        return first.isEmpty() ? "racine" : first;
    }

    public record Resolved(String actionCode, String entityType, String libelle) {
    }
}
