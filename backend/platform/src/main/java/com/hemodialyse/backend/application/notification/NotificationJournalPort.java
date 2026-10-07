package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Journal durable des alertes d'un centre : une alerte envoyée en temps réel y est aussi enregistrée, pour que les
 * personnes absentes (ou déconnectées) au moment de l'événement la retrouvent à leur connexion. Toujours borné au
 * centre (AGENTS.md §2) ; la visibilité d'une alerte dépend des rôles ciblés, la lecture est propre à chaque utilisateur.
 */
public interface NotificationJournalPort {

    void enregistrer(UUID id, UUID centerId, String type, Map<String, String> payload, Instant creeLe);

    /**
     * Alertes récentes du centre destinées à l'un des rôles (ou à tous), de la plus récente à la plus ancienne.
     *
     * @param roles rôles de l'utilisateur, sans préfixe {@code ROLE_}
     */
    PagedResult<Alerte> lister(UUID centerId, String userId, Set<String> roles, int page, int size);

    /**
     * Marque des alertes comme lues par l'utilisateur ; une alerte d'un autre centre est ignorée.
     */
    void marquerLues(UUID centerId, String userId, Collection<UUID> ids);

    /**
     * Marque comme lues toutes les alertes récentes visibles par l'utilisateur.
     */
    void toutMarquerLu(UUID centerId, String userId, Set<String> roles);

    /**
     * Supprime les alertes (et leurs lectures) plus anciennes que la date donnée, tous centres confondus.
     *
     * @return nombre d'alertes supprimées
     */
    int purger(Instant avant);

    /**
     * Alerte enregistrée.
     *
     * @param lue lue par l'utilisateur qui consulte le journal
     */
    record Alerte(UUID id, String type, Map<String, String> payload, Instant creeLe, boolean lue) {
    }
}
