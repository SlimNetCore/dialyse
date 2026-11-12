package com.hemodialyse.backend.application.audit;

import java.util.Locale;
import java.util.Map;
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

    /** Impression du cahier de dialyse (PL5, KB-PL5-3, décision du 2026-11-12) : lecture de données de santé, journalisée. */
    private static final String ROUTE_CAHIER = "/api/v1/cahier-dialyse/{patientId}/imprimer";

    /** Impression du bon d'intervention (PL6, KB-PL6-6, décision du 2026-11-12) : lecture journalisée comme l'impression du cahier. */
    private static final String ROUTE_BON = "/api/v1/gmao/interventions/{id}/bon";

    /** Impression du PV d'inventaire et téléchargement de la feuille de comptage (PL7, KB-PL7-5, décision du 2026-11-12). */
    private static final String ROUTE_SYNTHESE = "/api/v1/facturation/synthese/print";
    private static final String ROUTE_PV_INVENTAIRE = "/api/v1/stock/inventaires/{id}/rapport";
    private static final String ROUTE_FEUILLE_COMPTAGE = "/api/v1/stock/inventaires/{id}/feuille-comptage";

    /** Consultation du rapport PDF de la direction, en direct ou d'un mois figé (PL9, KB-PL9-8, décision du 2026-11-12). */
    private static final String ROUTE_RAPPORT_DIRECTION = "/api/v1/direction/report";
    private static final String ROUTE_RAPPORT_DIRECTION_MENSUEL = "/api/v1/direction/snapshots/{mois}/report";

    /** Liste et lecture des instantanés mensuels de la direction (PL10a, KB-PL10a-3, décision du 2026-11-12). */
    private static final String ROUTE_INSTANTANES = "/api/v1/direction/snapshots";
    private static final String ROUTE_INSTANTANE = "/api/v1/direction/snapshots/{mois}";

    /**
     * Gabarits de route (tels que renvoyés par Spring MVC) dont la simple lecture doit être tracée.
     */
    private static final Set<String> SENSITIVE_READS = Set.of(
            "/api/v1/patients/{patientId}/dossier-medical",
            ROUTE_CAHIER,
            ROUTE_BON,
            ROUTE_PV_INVENTAIRE,
            ROUTE_FEUILLE_COMPTAGE,
            ROUTE_RAPPORT_DIRECTION,
            ROUTE_RAPPORT_DIRECTION_MENSUEL,
            ROUTE_INSTANTANES,
            ROUTE_INSTANTANE
    );

    private static final Resolved ACTION_STATISTIQUES_PATIENT =
            new Resolved("STATISTIQUES_PATIENT_CONSULTATION", "patients", "Statistiques du patient consultées");

    /**
     * Libellés métier des écritures du référentiel des infirmiers (slice P2, décision du 26/10/2026 : KB-PLN-17), par
     * « MÉTHODE gabarit de route ». L'identifiant tracé est celui de la fiche (variable de chemin, ou fiche créée).
     */
    private static final Map<String, Resolved> ACTIONS_METIER = Map.ofEntries(
            Map.entry("POST /api/v1/infirmiers", new Resolved("INFIRMIER_ENREGISTRE", "infirmiers", "Infirmier enregistré")),
            Map.entry("PUT /api/v1/infirmiers/{id}", new Resolved("INFIRMIER_MODIFIE", "infirmiers", "Infirmier modifié")),
            Map.entry("POST /api/v1/infirmiers/{id}/desactiver", new Resolved("INFIRMIER_DESACTIVE", "infirmiers", "Infirmier désactivé")),
            Map.entry("POST /api/v1/infirmiers/{id}/reactiver", new Resolved("INFIRMIER_REACTIVE", "infirmiers", "Infirmier réactivé")),
            Map.entry("POST /api/v1/infirmiers/{id}/compte/lier", new Resolved("INFIRMIER_COMPTE_LIE", "infirmiers", "Compte lié à un infirmier")),
            Map.entry("POST /api/v1/infirmiers/{id}/compte/creer", new Resolved("INFIRMIER_COMPTE_CREE", "infirmiers", "Compte créé pour un infirmier")),
            Map.entry("DELETE /api/v1/infirmiers/{id}/compte", new Resolved("INFIRMIER_COMPTE_DELIE", "infirmiers", "Compte délié d'un infirmier")),
            // Slice P3 (décision du 26/10/2026, KB-PLN-33) : roulement, absences, « Mon planning », remplacements ; l'identifiant tracé
            // est celui de l'affectation, de l'absence ou du remplacement (créé ou visé).
            Map.entry("POST /api/v1/infirmiers/{id}/affectations", new Resolved("AFFECTATION_AJOUTEE", "infirmiers", "Affectation ajoutée au roulement")),
            Map.entry("PUT /api/v1/infirmiers/{id}/affectations/{affectationId}", new Resolved("AFFECTATION_MODIFIEE", "infirmiers", "Affectation modifiée")),
            Map.entry("DELETE /api/v1/infirmiers/{id}/affectations/{affectationId}", new Resolved("AFFECTATION_RETIREE", "infirmiers", "Affectation retirée du roulement")),
            Map.entry("POST /api/v1/infirmiers/absences", new Resolved("ABSENCE_DECLAREE", "infirmiers", "Absence d'infirmier déclarée")),
            Map.entry("DELETE /api/v1/infirmiers/absences/{id}", new Resolved("ABSENCE_RETIREE", "infirmiers", "Absence d'infirmier retirée")),
            Map.entry("POST /api/v1/infirmiers/moi/absences", new Resolved("ABSENCE_DECLAREE_PAR_L_INFIRMIER", "infirmiers", "Absence déclarée par l'infirmier")),
            Map.entry("DELETE /api/v1/infirmiers/moi/absences/{id}", new Resolved("ABSENCE_RETIREE_PAR_L_INFIRMIER", "infirmiers", "Absence retirée par l'infirmier")),
            Map.entry("POST /api/v1/infirmiers/presence/remplacements", new Resolved("REMPLACANT_AFFECTE", "infirmiers", "Remplaçant affecté")),
            Map.entry("DELETE /api/v1/infirmiers/presence/remplacements/{id}", new Resolved("REMPLACEMENT_ANNULE", "infirmiers", "Remplacement annulé")),
            // P5 (KB-PLN-62) : la sérologie n'est plus auditée sous l'action de la fiche du patient.
            Map.entry("POST /api/v1/patients/{patientId}/serologies", new Resolved("SEROLOGIE_ENREGISTREE", "patients", "Sérologie enregistrée")),
            Map.entry("PUT /api/v1/patients/{patientId}/serologies/{serologieId}", new Resolved("SEROLOGIE_MODIFIEE", "patients", "Sérologie modifiée")),
            Map.entry("DELETE /api/v1/patients/{patientId}/serologies/{serologieId}", new Resolved("SEROLOGIE_SUPPRIMEE", "patients", "Sérologie supprimée")),
            // Prise en charge (PC3, décision du 2026-11-03, KB-PAT-28) : la validation et la clôture ne sont plus journalisées
            // « PEC_CREATION » ; l'identifiant tracé est celui de la PEC (variable de chemin).
            Map.entry("POST /api/v1/pec/{pecId}/validate", new Resolved("PEC_VALIDATION", "pec", "PEC validée")),
            Map.entry("POST /api/v1/pec/{pecId}/close", new Resolved("PEC_CLOTURE", "pec", "PEC clôturée")),
            // Attestations de droits (PC4, décision du 2026-11-04, KB-PAT-43) : plus journalisées sous les actions de la PEC ;
            // l'identifiant tracé est celui de l'attestation (créée, ou variable de chemin).
            Map.entry("POST /api/v1/pec/attestations", new Resolved("ATTESTATION_CREATION", "attestations", "Attestation créée")),
            Map.entry("DELETE /api/v1/pec/attestations/{attestationId}",
                    new Resolved("ATTESTATION_SUPPRESSION", "attestations", "Attestation supprimée")),
            Map.entry("POST /api/v1/pec/attestations-center/search",
                    new Resolved("ATTESTATION_RECHERCHE", "attestations", "Recherche d'attestations")),
            // Statistiques du patient (PL4, décision du 2026-11-12, KB-PL4-4) : les recherches par corps sont des LECTURES ; elles ne sont plus
            // journalisées « PATIENTS_CREATION ». Le journal historique n'est pas réécrit.
            Map.entry("GET " + ROUTE_CAHIER, new Resolved("CAHIER_DIALYSE_IMPRESSION", "patients", "Cahier de dialyse imprimé")),
            // Synthèse mensuelle de facturation imprimée (PL8, KB-PL8-7, décision du 2026-11-12) : plus « FACTURATION_CREATION » ;
            // le libellé porte la période et le format (posés par le contrôleur), l'identifiant tracé est celui du centre.
            Map.entry("POST " + ROUTE_SYNTHESE, new Resolved("SYNTHESE_FACTURATION_IMPRESSION", "facturation", "Synthèse de facturation imprimée")),
            Map.entry("GET " + ROUTE_BON, new Resolved("BON_INTERVENTION_IMPRESSION", "gmao", "Bon d'intervention imprimé")),
            Map.entry("GET " + ROUTE_PV_INVENTAIRE, new Resolved("INVENTAIRE_PV_IMPRESSION", "stock", "Procès-verbal d'inventaire imprimé")),
            Map.entry("GET " + ROUTE_RAPPORT_DIRECTION, new Resolved("DIRECTION_RAPPORT_IMPRESSION", "direction", "Rapport de la direction consulté")),
            Map.entry("GET " + ROUTE_RAPPORT_DIRECTION_MENSUEL,
                    new Resolved("DIRECTION_RAPPORT_IMPRESSION", "direction", "Rapport mensuel de la direction consulté")),
            Map.entry("GET " + ROUTE_INSTANTANES,
                    new Resolved("DIRECTION_INSTANTANE_CONSULTATION", "direction", "Instantanés mensuels de la direction consultés")),
            Map.entry("GET " + ROUTE_INSTANTANE,
                    new Resolved("DIRECTION_INSTANTANE_CONSULTATION", "direction", "Instantané mensuel de la direction consulté")),
            Map.entry("GET " + ROUTE_FEUILLE_COMPTAGE,
                    new Resolved("INVENTAIRE_FEUILLE_TELECHARGEMENT", "stock", "Feuille de comptage téléchargée")),
            Map.entry("POST /api/v1/patients/stats/paramedical/search", ACTION_STATISTIQUES_PATIENT),
            Map.entry("POST /api/v1/patients/stats/medical/search", ACTION_STATISTIQUES_PATIENT),
            Map.entry("POST /api/v1/patients/stats/export/search", ACTION_STATISTIQUES_PATIENT));

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
        Resolved metier = ACTIONS_METIER.get(method + " " + routeTemplate);
        if (metier != null) return metier;
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
