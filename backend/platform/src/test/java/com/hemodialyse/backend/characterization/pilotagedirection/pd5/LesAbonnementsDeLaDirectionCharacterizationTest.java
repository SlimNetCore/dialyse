package com.hemodialyse.backend.characterization.pilotagedirection.pd5;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConstatsDuTableauDeBord.Lecture;
import com.hemodialyse.backend.contexts.pilotagedirection.application.EnregistrerLesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HorlogeDuPilotage;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SuivreLeTempsReel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteOuverte;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.TempsReelPublie;
import com.hemodialyse.backend.infrastructure.websocket.DirectionSubscriptionTracker;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD5 : filet du suivi des abonnements STOMP de la direction ({@code DirectionSubscriptionTracker}), écrit AVANT toute modification. Les
 * événements WebSocket sont fabriqués comme Spring les émet (message STOMP + identifiants de session et d'abonnement) ; le suivi du temps
 * réel est le vrai ({@code SuivreLeTempsReel} derrière {@code TempsReelPublie}), ses ports sortants sont des doubles en mémoire. Observation :
 * « société suivie ou non » (le comptage) et recalculs effectués par le balayage (l'effet sur {@code TempsReelPlanifie}, qui appelle ce balayage).
 */
class LesAbonnementsDeLaDirectionCharacterizationTest {

    private static final UUID SOCIETE = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private static final String TOPIC = "/topic/societe/";

    private final List<UUID> lectures = new ArrayList<>();
    private final SuivreLeTempsReel suivi = new SuivreLeTempsReel(
            societeId -> {
                lectures.add(societeId);
                return new Lecture(new ConstatDesIndicateurs(), List.of());
            },
            new EnregistrerLesAlertes(new HistoriqueVide(), new HorlogeDuPilotage(Clock.fixed(Instant.parse("2026-11-09T08:00:00Z"), ZoneOffset.UTC))),
            centreId -> Optional.empty(),
            (societeId, a, changements) -> {
            },
            new HorlogeDuPilotage(Clock.fixed(Instant.parse("2026-11-09T08:00:00Z"), ZoneOffset.UTC)));
    private final DirectionSubscriptionTracker tracker = new DirectionSubscriptionTracker(new TempsReelPublie(suivi));

    private static final class HistoriqueVide implements HistoriqueDesAlertes {
        @Override
        public List<AlerteOuverte> ouvertes(UUID societeId) {
            return List.of();
        }

        @Override
        public void ouvrir(UUID societeId, AlerteLevee alerte, OffsetDateTime vueLe) {
        }

        @Override
        public void resoudre(UUID alerteId, OffsetDateTime resolueLe) {
        }

        @Override
        public List<Entree> recentes(UUID societeId, int limite) {
            return List.of();
        }
    }

    private static Message<byte[]> message(StompCommand commande, String session, String abonnement, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(commande);
        accessor.setSessionId(session);
        if (abonnement != null) accessor.setSubscriptionId(abonnement);
        if (destination != null) accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void abonner(String session, String abonnement, String destination) {
        tracker.onSubscribe(new SessionSubscribeEvent(this, message(StompCommand.SUBSCRIBE, session, abonnement, destination), null));
    }

    private void desabonner(String session, String abonnement) {
        tracker.onUnsubscribe(new SessionUnsubscribeEvent(this, message(StompCommand.UNSUBSCRIBE, session, abonnement, null), null));
    }

    private void deconnecter(String session) {
        tracker.onDisconnect(new SessionDisconnectEvent(this, message(StompCommand.DISCONNECT, session, null, null), session,
                CloseStatus.NORMAL, null));
    }

    private List<UUID> recalculees() {
        lectures.clear();
        suivi.balayer();
        return List.copyOf(lectures);
    }

    @Test
    void un_abonnement_au_canal_d_une_societe_la_fait_suivre_avec_ou_sans_suite_de_chemin() {
        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");
        abonner("s2", "sub-0", TOPIC + AUTRE);

        assertThat(suivi.estSuivie(SOCIETE)).isTrue();
        assertThat(suivi.estSuivie(AUTRE)).isTrue();
        assertThat(recalculees()).containsExactlyInAnyOrder(SOCIETE, AUTRE);
    }

    @Test
    void les_autres_destinations_et_les_identifiants_illisibles_sont_ignores_sans_erreur() {
        abonner("s1", "sub-0", "/topic/center/" + SOCIETE + "/stock");
        abonner("s1", "sub-1", "/topic/societes/" + SOCIETE);
        abonner("s1", "sub-2", TOPIC + "pas-un-uuid/dashboard");
        abonner("s1", "sub-3", TOPIC);
        abonner("s1", "sub-4", null);

        assertThat(suivi.estSuivie(SOCIETE)).isFalse();
        assertThat(recalculees()).isEmpty();
    }

    @Test
    void le_meme_abonnement_signale_deux_fois_n_est_compte_qu_une_fois() {
        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");
        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");

        desabonner("s1", "sub-0");

        assertThat(suivi.estSuivie(SOCIETE)).isFalse();
    }

    @Test
    void la_societe_reste_suivie_tant_qu_un_abonnement_de_n_importe_quelle_session_subsiste() {
        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");
        abonner("s1", "sub-1", TOPIC + SOCIETE + "/dashboard");
        abonner("s2", "sub-0", TOPIC + SOCIETE + "/dashboard");

        desabonner("s1", "sub-0");
        assertThat(suivi.estSuivie(SOCIETE)).isTrue();
        desabonner("s1", "sub-1");
        assertThat(suivi.estSuivie(SOCIETE)).isTrue();
        desabonner("s2", "sub-0");
        assertThat(suivi.estSuivie(SOCIETE)).isFalse();
        assertThat(recalculees()).isEmpty();
    }

    @Test
    void se_desabonner_d_un_abonnement_inconnu_ne_retire_personne() {
        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");

        desabonner("s1", "sub-9");
        desabonner("s9", "sub-0");
        desabonner("s1", "sub-0");
        desabonner("s1", "sub-0");

        assertThat(suivi.estSuivie(SOCIETE)).isFalse();
    }

    @Test
    void la_deconnexion_libere_tous_les_abonnements_de_la_session_et_ceux_la_seulement() {
        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");
        abonner("s1", "sub-1", TOPIC + AUTRE + "/dashboard");
        abonner("s10", "sub-0", TOPIC + SOCIETE + "/dashboard");

        deconnecter("s1");

        assertThat(suivi.estSuivie(SOCIETE)).as("s10 reste abonnée : « s1/ » n'est pas préfixe de « s10/ »").isTrue();
        assertThat(suivi.estSuivie(AUTRE)).isFalse();
        deconnecter("s10");
        assertThat(suivi.estSuivie(SOCIETE)).isFalse();
        deconnecter("s10");
        assertThat(recalculees()).isEmpty();
    }

    @Test
    void sans_direction_connectee_le_balayage_ne_recalcule_rien_puis_recalcule_des_la_premiere_connexion() {
        assertThat(recalculees()).isEmpty();

        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");
        assertThat(recalculees()).containsExactly(SOCIETE);

        deconnecter("s1");
        assertThat(recalculees()).isEmpty();
    }

    @Test
    void l_abonnement_pose_le_constat_de_reference_par_un_signalement_traite_au_prochain_passage_rapide() {
        abonner("s1", "sub-0", TOPIC + SOCIETE + "/dashboard");

        lectures.clear();
        suivi.traiterLesSignalements();
        assertThat(lectures).containsExactly(SOCIETE);

        lectures.clear();
        suivi.traiterLesSignalements();
        assertThat(lectures).as("le signalement est consommé").isEmpty();
    }
}
