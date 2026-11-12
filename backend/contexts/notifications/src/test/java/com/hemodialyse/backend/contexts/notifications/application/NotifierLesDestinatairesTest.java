package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationInvalide;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.DiffusionEnregistree;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.notifications.doubles.NotificationsDuJournalEnMemoire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifierLesDestinatairesTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private NotificationsDuJournalEnMemoire journal;
    private DiffusionEnregistree diffusion;
    private NotifierLesDestinataires notifier;

    @BeforeEach
    void preparer() {
        journal = new NotificationsDuJournalEnMemoire();
        diffusion = new DiffusionEnregistree();
        notifier = new NotifierLesDestinataires(journal, diffusion, new HorlogeFixe(MAINTENANT), new IdentifiantsSequentiels());
    }

    private static Emission emission(Audience audience, UUID deterministe, boolean journalisee) {
        return new Emission(CENTRE, "GENERATEUR_INDISPONIBLE", Map.of("generateur", "G1"), audience, deterministe, journalisee);
    }

    @Test
    void should_record_a_durable_notification_and_broadcast_it_with_its_identifier() {
        assertThat(notifier.executer(emission(Audience.desRoles(List.of("ADMIN", "SECRETAIRE")), null, true))).isTrue();
        assertThat(journal.nombre()).isEqualTo(1);
        assertThat(diffusion.diffusions).hasSize(1);
        var d = diffusion.diffusions.getFirst();
        assertThat(d.identifiant()).isEqualTo(new UUID(0L, 1L));
        assertThat(d.centre()).isEqualTo(CENTRE);
        assertThat(d.type()).isEqualTo("GENERATEUR_INDISPONIBLE");
        assertThat(d.emisLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void should_carry_the_audience_in_the_content_with_the_keys_the_front_reads() {
        notifier.executer(emission(Audience.desRolesEtUnDestinataire(List.of("ADMIN"), "compte-inf"), null, true));
        assertThat(diffusion.diffusions.getFirst().contenu()).containsEntry("generateur", "G1").containsEntry("targetRoles", "ADMIN")
                .containsEntry("targetUserId", "compte-inf");
        notifier.executer(emission(Audience.desRoles(List.of()), null, true));
        assertThat(diffusion.diffusions.getLast().contenu()).containsEntry("targetRoles", "").doesNotContainKey("targetUserId");
    }

    @Test
    void should_only_broadcast_a_notification_that_is_not_journalised_without_identifier() {
        assertThat(notifier.executer(emission(Audience.desRoles(List.of()), null, false))).isTrue();
        assertThat(journal.nombre()).isZero();
        assertThat(diffusion.diffusions.getFirst().identifiant()).isNull();
    }

    @Test
    void should_neither_duplicate_nor_rebroadcast_a_replayed_fact_with_a_deterministic_identifier() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        assertThat(notifier.executer(emission(Audience.desRoles(List.of("ADMIN")), id, true))).isTrue();
        assertThat(notifier.executer(emission(Audience.desRoles(List.of("ADMIN")), id, true))).isFalse();
        assertThat(journal.nombre()).isEqualTo(1);
        assertThat(diffusion.diffusions).hasSize(1);
        assertThat(journal.existe(new NotificationId(id))).isTrue();
        assertThat(diffusion.diffusions.getFirst().identifiant()).isEqualTo(id);
    }

    @Test
    void should_not_broadcast_when_the_journal_fails() {
        journal.panne = new IllegalStateException("base indisponible");
        assertThatThrownBy(() -> notifier.executer(emission(Audience.desRoles(List.of()), null, true))).isInstanceOf(IllegalStateException.class);
        assertThat(diffusion.diffusions).isEmpty();
    }

    @Test
    void should_reject_a_request_without_centre_or_type() {
        assertThatThrownBy(() -> notifier.executer(new Emission(null, "T", Map.of(), Audience.desRoles(List.of()), null, true)))
                .isInstanceOf(NotificationInvalide.class);
        assertThatThrownBy(() -> notifier.executer(new Emission(CENTRE, " ", Map.of(), Audience.desRoles(List.of()), null, true)))
                .isInstanceOf(NotificationInvalide.class);
    }
}
