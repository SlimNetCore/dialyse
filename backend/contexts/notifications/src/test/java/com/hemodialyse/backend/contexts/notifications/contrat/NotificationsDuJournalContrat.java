package com.hemodialyse.backend.contexts.notifications.contrat;

import com.hemodialyse.backend.contexts.notifications.application.NotificationDuJournal;
import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du journal : le double en mémoire et l'adapter JDBC passent exactement ces tests. */
public abstract class NotificationsDuJournalContrat {

    protected static final Instant MAINTENANT = Instant.parse("2026-11-12T12:00:00Z");
    protected static final Instant DEPUIS = MAINTENANT.minusSeconds(30L * 86400);
    protected static final CentreId A = new CentreId(UUID.fromString("99998141-0000-0000-0000-00000000000a"));
    protected static final CentreId B = new CentreId(UUID.fromString("99998141-0000-0000-0000-00000000000b"));

    protected abstract NotificationsDuJournal journal();

    protected abstract int lectures(String utilisateur);

    protected static NotificationId id(int n) {
        return new NotificationId(UUID.fromString(String.format("99998141-0000-0000-0000-0000000001%02d", n)));
    }

    protected void emise(int n, CentreId centre, Audience audience, long minutes) {
        journal().enregistrer(Notification.emise(id(n), centre, new TypeDeNotification("T" + n), Map.of("cle", "v" + n), audience,
                MAINTENANT.minusSeconds(minutes * 60)));
    }

    protected static Audience pour(String... roles) {
        return Audience.desRoles(Arrays.asList(roles));
    }

    private List<Integer> numeros(List<NotificationDuJournal> items) {
        return items.stream().map(i -> Integer.parseInt(i.id().valeur().toString().substring(34))).toList();
    }

    private List<Integer> liste(CentreId centre, String utilisateur, Set<String> roles) {
        return numeros(journal().lister(centre, utilisateur, roles, DEPUIS, 0, 100).items());
    }

    @Test
    void should_tell_whether_an_identifier_is_already_in_the_journal() {
        emise(1, A, pour("ADMIN"), 5);
        assertThat(journal().existe(id(1))).isTrue();
        assertThat(journal().existe(id(2))).isFalse();
    }

    @Test
    void should_list_newest_first_with_the_total_and_the_page_asked() {
        for (int n = 1; n <= 5; n++) emise(n, A, pour(), 100 - n);
        var page0 = journal().lister(A, "u", Set.of(), DEPUIS, 0, 2);
        var page2 = journal().lister(A, "u", Set.of(), DEPUIS, 2, 2);
        assertThat(numeros(page0.items())).containsExactly(5, 4);
        assertThat(numeros(page2.items())).containsExactly(1);
        assertThat(page0.total()).isEqualTo(5);
        assertThat(page0.page()).isZero();
        assertThat(page2.size()).isEqualTo(2);
        assertThat(journal().lister(A, "u", Set.of(), DEPUIS, 9, 2).items()).isEmpty();
    }

    @Test
    void should_return_the_content_the_type_and_the_date_as_recorded() {
        Map<String, String> contenu = new HashMap<>();
        contenu.put("patientNom", "BENALI Karim");
        contenu.put("accent", "é à ü");
        contenu.put("vide", null);
        journal().enregistrer(Notification.emise(id(1), A, new TypeDeNotification("GENERATEUR_INDISPONIBLE"), contenu, pour("ADMIN"), MAINTENANT));
        NotificationDuJournal lue = journal().lister(A, "u", Set.of("ADMIN"), DEPUIS, 0, 10).items().getFirst();
        assertThat(lue.type().valeur()).isEqualTo("GENERATEUR_INDISPONIBLE");
        assertThat(lue.contenu()).containsEntry("patientNom", "BENALI Karim").containsEntry("accent", "é à ü").containsEntry("vide", null);
        assertThat(lue.creeLe()).isEqualTo(MAINTENANT);
        assertThat(lue.lue()).isFalse();
    }

    @Test
    void should_only_list_the_centre_asked_and_the_window() {
        emise(1, A, pour(), 10);
        emise(2, B, pour(), 10);
        emise(3, A, pour(), 31L * 24 * 60);
        assertThat(liste(A, "u", Set.of())).containsExactly(1);
        assertThat(liste(B, "u", Set.of())).containsExactly(2);
    }

    @Test
    void should_show_a_notification_to_the_roles_of_its_audience_and_to_everybody_when_it_has_none() {
        emise(1, A, pour("ADMIN"), 40);
        emise(2, A, pour("ADMIN", "SECRETAIRE"), 30);
        emise(3, A, pour(), 20);
        emise(4, A, pour("INFIRMIER"), 10);
        assertThat(liste(A, "u", Set.of("ADMIN"))).containsExactly(3, 2, 1);
        assertThat(liste(A, "u", Set.of("SECRETAIRE"))).containsExactly(3, 2);
        assertThat(liste(A, "u", Set.of("MEDECIN"))).containsExactly(3);
        assertThat(liste(A, "u", Set.of())).containsExactly(3);
    }

    @Test
    void should_compare_roles_exactly_the_underscore_and_the_percent_are_not_wildcards_kb_not1_1() {
        emise(1, A, pour("GMAO_REFORME"), 20);
        emise(2, A, pour("GMAOXREFORME"), 10);
        assertThat(liste(A, "u", Set.of("GMAO_REFORME"))).containsExactly(1);
        assertThat(liste(A, "u", Set.of("GMAOXREFORME"))).containsExactly(2);
        assertThat(liste(A, "u", Set.of("GMAO_RECTIFICATION"))).isEmpty();
        assertThat(liste(A, "u", Set.of("%"))).isEmpty();
        assertThat(liste(A, "u", Set.of("ADMIN%"))).isEmpty();
    }

    @Test
    void should_show_a_personal_notification_to_its_addressee_and_to_the_roles_of_its_audience() {
        journal().enregistrer(Notification.emise(id(1), A, new TypeDeNotification("INFIRMIER_ROULEMENT_MODIFIE"), Map.of(),
                Audience.desRolesEtUnDestinataire(List.of("ADMIN"), "compte-inf"), MAINTENANT.minusSeconds(60)));
        assertThat(liste(A, "compte-inf", Set.of("INFIRMIER"))).containsExactly(1);
        assertThat(liste(A, "autre", Set.of("INFIRMIER"))).isEmpty();
        assertThat(liste(A, "autre", Set.of("ADMIN"))).containsExactly(1);
    }

    @Test
    void should_keep_the_reading_of_each_user_apart_and_mark_idempotently() {
        emise(1, A, pour("ADMIN"), 10);
        journal().marquerLues(A, "admin", Set.of("ADMIN"), List.of(id(1), id(1)), MAINTENANT);
        journal().marquerLues(A, "admin", Set.of("ADMIN"), List.of(id(1)), MAINTENANT);
        assertThat(journal().lister(A, "admin", Set.of("ADMIN"), DEPUIS, 0, 10).items()).extracting(NotificationDuJournal::lue).containsExactly(true);
        assertThat(journal().lister(A, "admin2", Set.of("ADMIN"), DEPUIS, 0, 10).items()).extracting(NotificationDuJournal::lue).containsExactly(false);
        assertThat(lectures("admin")).isEqualTo(1);
    }

    @Test
    void should_ignore_the_marking_of_unknown_other_centre_or_other_audience_notifications_kb_not1_3() {
        emise(1, B, pour("ADMIN"), 10);
        emise(2, A, pour("INFIRMIER"), 10);
        emise(3, A, pour("ADMIN"), 10);
        journal().marquerLues(A, "admin", Set.of("ADMIN"), List.of(id(1), id(2), id(3), id(9)), MAINTENANT);
        assertThat(lectures("admin")).isEqualTo(1);
        assertThat(journal().lister(A, "admin", Set.of("ADMIN"), DEPUIS, 0, 10).items().getFirst().lue()).isTrue();
    }

    @Test
    void should_mark_a_notification_older_than_the_window_when_it_is_of_the_audience() {
        emise(1, A, pour("ADMIN"), 40L * 24 * 60);
        journal().marquerLues(A, "admin", Set.of("ADMIN"), List.of(id(1)), MAINTENANT);
        assertThat(lectures("admin")).isEqualTo(1);
    }

    @Test
    void should_mark_everything_visible_in_the_window_of_the_centre_as_read_and_nothing_else() {
        emise(1, A, pour("ADMIN"), 10);
        emise(2, A, pour(), 10);
        emise(3, A, pour("INFIRMIER"), 10);
        emise(4, B, pour("ADMIN"), 10);
        emise(5, A, pour("ADMIN"), 40L * 24 * 60);
        journal().toutMarquerLu(A, "admin", Set.of("ADMIN"), DEPUIS, MAINTENANT);
        journal().toutMarquerLu(A, "admin", Set.of("ADMIN"), DEPUIS, MAINTENANT);
        assertThat(lectures("admin")).isEqualTo(2);
    }

    @Test
    void should_purge_the_older_notifications_with_their_readings_for_every_centre() {
        emise(1, A, pour("ADMIN"), 61L * 24 * 60);
        emise(2, A, pour("ADMIN"), 59L * 24 * 60);
        emise(3, B, pour("ADMIN"), 90L * 24 * 60);
        journal().marquerLues(A, "admin", Set.of("ADMIN"), List.of(id(1), id(2)), MAINTENANT);
        assertThat(journal().purger(MAINTENANT.minusSeconds(60L * 86400))).isEqualTo(2);
        assertThat(journal().existe(id(1))).isFalse();
        assertThat(journal().existe(id(2))).isTrue();
        assertThat(journal().existe(id(3))).isFalse();
        assertThat(lectures("admin")).isEqualTo(1);
        assertThat(journal().purger(MAINTENANT.minusSeconds(60L * 86400))).isZero();
    }
}
