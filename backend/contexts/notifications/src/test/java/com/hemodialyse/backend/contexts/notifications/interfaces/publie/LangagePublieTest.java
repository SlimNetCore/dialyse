package com.hemodialyse.backend.contexts.notifications.interfaces.publie;

import com.hemodialyse.backend.contexts.notifications.application.NotifierLesDestinataires;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.DiffusionEnregistree;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.notifications.doubles.Doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.notifications.doubles.NotificationsDuJournalEnMemoire;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Traduction du langage publié {@code notifications.v1} vers le modèle du contexte (table-driven). */
class LangagePublieTest {

    private static final Instant T = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final NotificationsDuJournalEnMemoire journal = new NotificationsDuJournalEnMemoire();
    private final JournalDesNotificationsPublie publie = new JournalDesNotificationsPublie(journal);

    @ParameterizedTest(name = "targetRoles=[{0}] targetUserId=[{1}] -> visible par {2} : {3}")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "ADMIN|NULL|ADMIN|true",
            "ADMIN|NULL|MEDECIN|false",
            "ADMIN,SECRETAIRE|NULL|SECRETAIRE|true",
            "' ADMIN , SECRETAIRE '|NULL|SECRETAIRE|true",
            "''|NULL|MEDECIN|true",
            "ADMIN|compte-inf|INFIRMIER|true",
            "GMAO_REFORME|NULL|GMAOXREFORME|false"})
    void should_read_the_audience_from_the_keys_of_the_legacy_content(String roles, String compte, String role, boolean visible) {
        Map<String, String> contenu = new java.util.HashMap<>();
        if (roles != null) contenu.put("targetRoles", roles);
        if (compte != null) contenu.put("targetUserId", compte);
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        publie.enregistrer(id, CENTRE, "T", contenu, T);
        assertThat(publie.existe(id)).isTrue();
        var vue = journal.lister(new com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId(CENTRE), "compte-inf", Set.of(role),
                T.minusSeconds(60), 0, 10);
        assertThat(vue.items().size() == 1).isEqualTo(visible);
    }

    @Test
    void should_translate_a_request_to_the_use_case_and_report_whether_it_was_emitted() {
        var diffusion = new DiffusionEnregistree();
        var notifier = new NotifierPublie(new NotifierLesDestinataires(journal, diffusion, new HorlogeFixe(T), new IdentifiantsSequentiels()));
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        var demande = new DemandeDeNotification(CENTRE, "OPTIMISATION_PROPOSITION", Map.of("k", "v"),
                Audience.desRolesEtUnDestinataire(List.of("ADMIN"), "cpt"), id, true);
        assertThat(notifier.notifier(demande)).isTrue();
        assertThat(notifier.notifier(demande)).isFalse();
        assertThat(journal.existe(new NotificationId(id))).isTrue();
        assertThat(diffusion.diffusions).hasSize(1);
        assertThat(diffusion.diffusions.getFirst().contenu()).containsEntry("targetRoles", "ADMIN").containsEntry("targetUserId", "cpt");
    }
}
