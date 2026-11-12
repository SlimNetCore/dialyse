package com.hemodialyse.backend.contexts.notifications.configuration;

import com.hemodialyse.backend.contexts.notifications.application.ConsulterLeJournal;
import com.hemodialyse.backend.contexts.notifications.application.Horloge;
import com.hemodialyse.backend.contexts.notifications.application.Identifiants;
import com.hemodialyse.backend.contexts.notifications.application.MarquerLesNotificationsLues;
import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.application.NotifierLesDestinataires;
import com.hemodialyse.backend.contexts.notifications.application.PurgerLesNotifications;
import com.hemodialyse.backend.contexts.notifications.application.SessionCourante;
import com.hemodialyse.backend.contexts.notifications.application.ToutMarquerLu;
import com.hemodialyse.backend.contexts.notifications.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.notifications.infrastructure.persistence.NotificationsDuJournalJdbc;
import com.hemodialyse.backend.contexts.notifications.infrastructure.systeme.HorlogeDuSysteme;
import com.hemodialyse.backend.contexts.notifications.infrastructure.systeme.IdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.notifications.interfaces.planifie.PurgeNocturneDesNotifications;
import com.hemodialyse.backend.contexts.notifications.interfaces.publie.JournalDesNotificationsPublie;
import com.hemodialyse.backend.contexts.notifications.interfaces.publie.NotifierPublie;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Notifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Composition du contexte Notifications (NOT1, ADR 0140, 0141) : le journal, les trois cas d'usage servis par
 * {@code /api/v1/notifications}, la purge de 03:40 et le langage publié {@code notifications.v1}. Mêmes tables et mêmes URL que la
 * plateforme d'avant ; aucun flag, aucune façade. Le transport temps réel est le port {@link DiffusionEnTempsReel}, fourni par la plateforme.
 */
@Configuration(proxyBeanMethods = false)
public class NotificationsConfiguration {

    @Bean
    SessionCourante sessionCouranteDesNotifications() {
        return new SessionLegacy();
    }

    @Bean
    Horloge horlogeDesNotifications() {
        return new HorlogeDuSysteme();
    }

    @Bean
    Identifiants identifiantsDesNotifications() {
        return new IdentifiantsAleatoires();
    }

    @Bean
    NotificationsDuJournal notificationsDuJournal(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new NotificationsDuJournalJdbc(jdbc, mapper);
    }

    @Bean
    ConsulterLeJournal consulterLeJournal(SessionCourante session, NotificationsDuJournal journal, Horloge horloge) {
        return new ConsulterLeJournal(session, journal, horloge);
    }

    @Bean
    MarquerLesNotificationsLues marquerLesNotificationsLues(SessionCourante session, NotificationsDuJournal journal, Horloge horloge) {
        return new MarquerLesNotificationsLues(session, journal, horloge);
    }

    @Bean
    ToutMarquerLu toutMarquerLu(SessionCourante session, NotificationsDuJournal journal, Horloge horloge) {
        return new ToutMarquerLu(session, journal, horloge);
    }

    @Bean
    PurgerLesNotifications purgerLesNotifications(NotificationsDuJournal journal, Horloge horloge) {
        return new PurgerLesNotifications(journal, horloge);
    }

    @Bean
    PurgeNocturneDesNotifications purgeNocturneDesNotifications(PurgerLesNotifications purge) {
        return new PurgeNocturneDesNotifications(purge);
    }

    @Bean
    NotifierLesDestinataires notifierLesDestinataires(NotificationsDuJournal journal, DiffusionEnTempsReel diffusion, Horloge horloge,
                                                      Identifiants identifiants) {
        return new NotifierLesDestinataires(journal, diffusion, horloge, identifiants);
    }

    @Bean
    Notifier notifier(NotifierLesDestinataires cas) {
        return new NotifierPublie(cas);
    }

    @Bean
    JournalDesNotifications journalDesNotifications(NotificationsDuJournal journal) {
        return new JournalDesNotificationsPublie(journal);
    }
}
