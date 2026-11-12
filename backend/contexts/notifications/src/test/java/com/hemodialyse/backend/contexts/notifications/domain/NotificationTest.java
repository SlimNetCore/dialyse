package com.hemodialyse.backend.contexts.notifications.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final NotificationId ID = new NotificationId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

    private static Notification notification(Map<String, String> contenu, Audience audience) {
        return Notification.emise(ID, CENTRE, new TypeDeNotification("GENERATEUR_INDISPONIBLE"), contenu, audience, T0);
    }

    @Test
    void une_notification_est_rattachee_a_un_centre_et_porte_son_type_son_contenu_et_son_audience() {
        Notification n = notification(Map.of("generateur", "G1"), Audience.desRoles(List.of("ADMIN")));
        assertThat(n.centre()).isEqualTo(CENTRE);
        assertThat(n.type().valeur()).isEqualTo("GENERATEUR_INDISPONIBLE");
        assertThat(n.contenu()).containsEntry("generateur", "G1");
        assertThat(n.audience().roles()).containsExactly("ADMIN");
    }

    @Test
    void le_contenu_de_la_notification_est_isole_de_la_map_de_l_emetteur() {
        Map<String, String> source = new HashMap<>();
        source.put("a", "1");
        Notification n = notification(source, Audience.desRoles(List.of()));
        source.put("b", "2");
        assertThat(n.contenu()).containsOnlyKeys("a");
        assertThatThrownBy(() -> n.contenu().put("c", "3")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void le_contenu_peut_porter_une_valeur_absente_comme_les_emetteurs_actuels() {
        Map<String, String> source = new HashMap<>();
        source.put("patientNom", null);
        assertThat(notification(source, Audience.desRoles(List.of())).contenu()).containsKey("patientNom");
    }

    @Test
    void un_type_de_notification_vide_est_refuse() {
        assertThatThrownBy(() -> new TypeDeNotification("  ")).isInstanceOf(NotificationInvalide.class);
        assertThatThrownBy(() -> new TypeDeNotification(null)).isInstanceOf(NotificationInvalide.class);
    }

    @Test
    void une_notification_sans_centre_ni_identifiant_est_refusee() {
        assertThatThrownBy(() -> Notification.emise(null, CENTRE, new TypeDeNotification("X"), Map.of(), Audience.desRoles(List.of()), T0))
                .isInstanceOf(NotificationInvalide.class);
        assertThatThrownBy(() -> Notification.emise(ID, null, new TypeDeNotification("X"), Map.of(), Audience.desRoles(List.of()), T0))
                .isInstanceOf(NotificationInvalide.class);
        assertThatThrownBy(() -> Notification.emise(ID, CENTRE, new TypeDeNotification("X"), Map.of(), Audience.desRoles(List.of()), null))
                .isInstanceOf(NotificationInvalide.class);
    }

    @Test
    void une_notification_est_visible_selon_son_audience() {
        Notification n = notification(Map.of(), Audience.desRolesEtUnDestinataire(List.of("ADMIN"), "inf"));
        assertThat(n.estVisiblePar(Set.of("ADMIN"), "x")).isTrue();
        assertThat(n.estVisiblePar(Set.of("INFIRMIER"), "inf")).isTrue();
        assertThat(n.estVisiblePar(Set.of("INFIRMIER"), "x")).isFalse();
    }

    @Test
    void le_journal_propose_trente_jours_et_conserve_soixante_jours() {
        assertThat(DureesDuJournal.FENETRE_DE_CONSULTATION).isEqualTo(Duration.ofDays(30));
        assertThat(DureesDuJournal.CONSERVATION).isEqualTo(Duration.ofDays(60));
    }
}
