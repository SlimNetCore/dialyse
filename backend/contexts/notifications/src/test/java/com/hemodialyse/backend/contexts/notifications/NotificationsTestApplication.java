package com.hemodialyse.backend.contexts.notifications;

import org.springframework.boot.SpringBootConfiguration;

/**
 * Racine de test du module : les tests de contrat s'exécutent sans l'application de la plateforme. Volontairement sans balayage ni
 * auto-configuration : seuls les adapters sont instanciés à la main par les tests.
 */
@SpringBootConfiguration
public class NotificationsTestApplication {
}
