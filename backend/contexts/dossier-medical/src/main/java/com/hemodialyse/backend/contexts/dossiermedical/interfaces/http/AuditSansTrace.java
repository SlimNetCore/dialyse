package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

/**
 * Attribut de requête lu par le filtre d'audit de la plateforme : la requête n'a rien écrit (suppression d'une fiche inexistante,
 * qui répond 204), aucune ligne d'audit. Définition unique pour les contrôleurs du contexte ; le contexte ne dépend pas de la
 * plateforme, l'égalité avec {@code AuditRequestFilter.ATTRIBUT_SANS_TRACE} est figée par {@code AttributSansTraceDeLAuditTest}.
 */
public final class AuditSansTrace {

    public static final String ATTRIBUT = "hemodialyse.audit.sansTrace";

    private AuditSansTrace() {
    }
}
