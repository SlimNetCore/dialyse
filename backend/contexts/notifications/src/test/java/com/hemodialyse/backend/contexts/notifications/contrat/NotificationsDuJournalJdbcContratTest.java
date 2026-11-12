package com.hemodialyse.backend.contexts.notifications.contrat;

import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.infrastructure.persistence.NotificationsDuJournalJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Le journal sur H2 (mode PostgreSQL), tables calquées sur V29 et V49 : mêmes tests de contrat que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:notifications-journal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationsDuJournalJdbcContratTest extends NotificationsDuJournalContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS notification_evenement (id UUID PRIMARY KEY, center_id UUID NOT NULL, type VARCHAR(60) NOT NULL, "
                + "payload TEXT NOT NULL, target_roles VARCHAR(255) NOT NULL, cree_le TIMESTAMP NOT NULL, target_user_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS notification_lecture (notification_id UUID NOT NULL, user_id VARCHAR(100) NOT NULL, "
                + "lue_le TIMESTAMP NOT NULL, PRIMARY KEY (notification_id, user_id))");
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.execute("DELETE FROM notification_lecture");
        jdbc.execute("DELETE FROM notification_evenement");
    }

    @Override
    protected NotificationsDuJournal journal() {
        return new NotificationsDuJournalJdbc(jdbc, JsonMapper.builder().build());
    }

    @Override
    protected int lectures(String utilisateur) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_lecture WHERE user_id = ?", Integer.class, utilisateur);
    }
}
