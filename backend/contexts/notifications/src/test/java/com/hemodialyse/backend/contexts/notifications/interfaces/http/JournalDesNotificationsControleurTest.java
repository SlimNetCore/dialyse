package com.hemodialyse.backend.contexts.notifications.interfaces.http;

import com.hemodialyse.backend.contexts.notifications.application.ConsulterLeJournal;
import com.hemodialyse.backend.contexts.notifications.application.MarquerLesNotificationsLues;
import com.hemodialyse.backend.contexts.notifications.application.ToutMarquerLu;
import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.NotificationsDuJournalEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les trois routes : mêmes URL, mêmes champs JSON, mêmes codes d'erreur ; les entrées illisibles sont des 400 (KB-NOT1-2). */
class JournalDesNotificationsControleurTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-12T12:00:00Z");
    private static final CentreId A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final UUID N1 = new UUID(0L, 1L);

    private final NotificationsDuJournalEnMemoire journal = new NotificationsDuJournalEnMemoire();
    private MockMvc mvc;

    @BeforeEach
    void preparer() {
        var session = new SessionFixe(A, "admin", Set.of("ADMIN"));
        var horloge = new HorlogeFixe(MAINTENANT);
        var controleur = new JournalDesNotificationsControleur(new ConsulterLeJournal(session, journal, horloge),
                new MarquerLesNotificationsLues(session, journal, horloge), new ToutMarquerLu(session, journal, horloge));
        mvc = MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuJournalAdvice()).build();
        journal.enregistrer(Notification.emise(new NotificationId(N1), A, new TypeDeNotification("GENERATEUR_INDISPONIBLE"),
                Map.of("generateur", "G1"), Audience.desRoles(List.of("ADMIN")), MAINTENANT.minusSeconds(60)));
    }

    @Test
    void should_serve_the_journal_with_the_json_fields_of_the_legacy_and_no_store() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].id").value(N1.toString()))
                .andExpect(jsonPath("$.items[0].type").value("GENERATEUR_INDISPONIBLE"))
                .andExpect(jsonPath("$.items[0].centerId").value(A.valeur().toString()))
                .andExpect(jsonPath("$.items[0].payload.generateur").value("G1"))
                .andExpect(jsonPath("$.items[0].timestamp").value("2026-11-12T11:59:00Z"))
                .andExpect(jsonPath("$.items[0].lue").value(false))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void should_mark_as_read_then_answer_204_and_serve_the_state() throws Exception {
        mvc.perform(post("/api/v1/notifications/lecture").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + N1 + "\"]}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications")).andExpect(jsonPath("$.items[0].lue").value(true));
        mvc.perform(post("/api/v1/notifications/lecture-tout")).andExpect(status().isNoContent());
        assertThat(journal.lecturesDe("admin")).isEqualTo(1);
    }

    @Test
    void should_answer_400_bad_request_to_a_page_or_a_size_that_cannot_be_read() throws Exception {
        for (String q : List.of("page=abc", "size=abc", "page=1.5", "page=99999999999", "centerId=pas-un-uuid")) {
            mvc.perform(get("/api/v1/notifications?" + q)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
    }

    @Test
    void should_answer_400_to_a_missing_or_broken_body_and_a_validation_error_to_missing_ids() throws Exception {
        mvc.perform(post("/api/v1/notifications/lecture")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/notifications/lecture").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":["))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/notifications/lecture").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"pas-un-uuid\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/notifications/lecture").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/notifications/lecture").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/notifications/lecture").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[null]}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_answer_403_access_denied_to_another_centre() throws Exception {
        UUID autre = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        mvc.perform(get("/api/v1/notifications?centerId=" + autre)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED")).andExpect(jsonPath("$.title").value("Acces refuse"))
                .andExpect(jsonPath("$.detail").value("Accès refusé : le centre demandé ne correspond pas à la session"));
        mvc.perform(post("/api/v1/notifications/lecture-tout?centerId=" + autre)).andExpect(status().isForbidden());
    }
}
