package com.hemodialyse.backend.contexts.gmao;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/** Racine de test du module : les tests d'intégration du contexte s'exécutent sans l'application legacy. */
@SpringBootApplication
public class GmaoTestApplication {

    /** Notifications est fourni par la plateforme : ici, un émetteur qui ne journalise ni ne diffuse rien (PL13d). */
    @Configuration(proxyBeanMethods = false)
    static class NotificationsAbsentes {

        @Bean
        NotifierEnDeuxTemps notifier() {
            return demande -> Optional.empty();
        }

        @Bean
        DiffusionEnTempsReel diffusion() {
            return (centre, type, contenu, identifiant, emisLe) -> {
            };
        }
    }
}
