package com.hemodialyse.backend.contexts.absencespatients.interfaces;

import com.hemodialyse.backend.contexts.absencespatients.application.ControlerLesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ControlerLesAbsences.ResultatDuCentre;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.jobs.ControleDeNuitDesAbsences;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les déclencheurs du contrôle de nuit : un seul cron à 02:30 (fuseau par défaut du centre), un rattrapage au démarrage
 * désactivable, jamais de notification au démarrage (portée par le cas d'usage).
 */
class ControleDeNuitDesAbsencesTest {

    private final List<String> appels = new ArrayList<>();
    private final ControlerLesAbsences controler = new ControlerLesAbsences(List::of, null, null, null, null) {
        @Override
        public List<ResultatDuCentre> controlerLaNuit() {
            appels.add("nuit");
            return List.of();
        }

        @Override
        public List<ResultatDuCentre> rattraperAuDemarrage() {
            appels.add("démarrage");
            return List.of();
        }
    };
    private final ControleDeNuitDesAbsences job = new ControleDeNuitDesAbsences(controler);

    @Test
    void le_controle_de_nuit_est_declenche_chaque_jour_a_deux_heures_trente_au_fuseau_par_defaut() throws Exception {
        Scheduled planification = ControleDeNuitDesAbsences.class.getMethod("controlerAbsences").getAnnotation(Scheduled.class);

        assertThat(planification.cron()).isEqualTo("0 30 2 * * *");
        // une seule clé de fuseau pour les jobs de nuit (revue P7) : la clé des Absences n'est qu'une surcharge facultative
        assertThat(planification.zone())
                .isEqualTo("${app.absences.fuseau-par-defaut:${app.planification.fuseau-par-defaut:Africa/Algiers}}");
    }

    @Test
    void le_rattrapage_est_declenche_quand_l_application_est_prete() throws Exception {
        EventListener ecoute = ControleDeNuitDesAbsences.class.getMethod("rattraperAuDemarrage").getAnnotation(EventListener.class);

        assertThat(ecoute.value()).containsExactly(ApplicationReadyEvent.class);
    }

    @Test
    void le_declencheur_de_nuit_lance_le_controle_de_nuit() {
        job.controlerAbsences();

        assertThat(appels).containsExactly("nuit");
    }

    @Test
    void le_rattrapage_au_demarrage_est_actif_par_defaut_et_desactivable() {
        job.rattraperAuDemarrage();
        ReflectionTestUtils.setField(job, "rattrapageAuDemarrage", false);
        job.rattraperAuDemarrage();

        assertThat(appels).containsExactly("démarrage");
    }
}
