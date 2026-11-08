package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.FactureId;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.JOUR;
import static com.hemodialyse.backend.contexts.seance.application.Banc.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE6 : changement du jour d'une séance, ports en mémoire (ADR 0093). */
class ChangerLeJourDUneSeanceTest {

    private static final FactureId FACTURE = new FactureId(new UUID(8, 1));

    private Banc banc;
    private PatientId patient;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        banc.session.administrateur();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
    }

    private Seance semer(int n, StatutDeSeance statut) {
        var s = banc.ligne(CENTRE_A, patient, n, statut);
        banc.seances.semer(s);
        return s;
    }

    private Seance relue(Seance s) {
        return banc.seances.trouver(CENTRE_A, s.id()).orElseThrow();
    }

    @Test
    void should_change_the_day_under_the_row_lock_and_return_the_session() {
        var s = semer(1, StatutDeSeance.SIGNEE);

        var resultat = banc.changerLeJour().executer(CENTRE_A.valeur(), s.id().valeur(), () -> JOUR.plusDays(2));

        assertThat(resultat.jour()).isEqualTo(JOUR.plusDays(2));
        assertThat(relue(s).jour()).isEqualTo(JOUR.plusDays(2));
        assertThat(relue(s).statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_take_the_day_of_the_centre_when_the_body_gives_none() {
        var s = semer(1, StatutDeSeance.CREE);
        banc.horlogeA("2026-06-15T23:30:00Z"); // Alger (UTC+1) : le jour du centre est déjà le 16

        banc.changerLeJour().executer(CENTRE_A.valeur(), s.id().valeur(), () -> null);

        assertThat(relue(s).jour()).isEqualTo(LocalDate.of(2026, 6, 16));
    }

    @Test
    void should_keep_the_invoice_the_session_is_attached_to() {
        var avec = Seance.restituer(new com.hemodialyse.backend.contexts.seance.domain.SeanceId(new UUID(5, 1)), CENTRE_A, patient, JOUR,
                StatutDeSeance.SIGNEE, MAINTENANT, null, null, null, null, FACTURE);
        banc.seances.semer(avec);

        banc.changerLeJour().executer(CENTRE_A.valeur(), avec.id().valeur(), () -> JOUR.plusDays(1));

        assertThat(relue(avec).facture()).contains(FACTURE);
    }

    @Test
    void should_refuse_a_billed_session_after_finding_it() {
        var s = semer(1, StatutDeSeance.FACTUREE);

        assertThatThrownBy(() -> banc.changerLeJour().executer(CENTRE_A.valeur(), s.id().valeur(), () -> JOUR.plusDays(1)))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"));
        assertThat(relue(s).jour()).isEqualTo(JOUR);
    }

    @Test
    void should_report_an_unknown_session_or_one_of_another_centre_as_not_found() {
        var autre = banc.ligne(CENTRE_B, banc.patient(CENTRE_B, 2, "P002", "A2"), 2, StatutDeSeance.CREE);
        banc.seances.semer(autre);

        assertThatThrownBy(() -> banc.changerLeJour().executer(CENTRE_A.valeur(), new UUID(5, 99), () -> JOUR))
                .isInstanceOf(SeanceIntrouvable.class);
        assertThatThrownBy(() -> banc.changerLeJour().executer(CENTRE_A.valeur(), autre.id().valeur(), () -> JOUR))
                .isInstanceOf(SeanceIntrouvable.class);
    }

    @Test
    void should_refuse_a_foreign_centre_before_reading_the_day() {
        var s = semer(1, StatutDeSeance.CREE);
        boolean[] lu = {false};

        assertThatThrownBy(() -> banc.changerLeJour().executer(CENTRE_B.valeur(), s.id().valeur(), () -> {
            lu[0] = true;
            throw new SaisieInvalide("jour illisible");
        })).isInstanceOf(AccesRefuse.class);
        assertThat(lu[0]).isFalse();
    }

    @Test
    void should_refuse_an_unreadable_day_before_looking_for_the_session() {
        assertThatThrownBy(() -> banc.changerLeJour().executer(CENTRE_A.valeur(), new UUID(5, 99), () -> {
            throw new SaisieInvalide("jour illisible");
        })).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_an_absent_session_identifier() {
        assertThatThrownBy(() -> banc.changerLeJour().executer(CENTRE_A.valeur(), null, () -> JOUR)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_not_hide_an_unreadable_row_behind_a_class_name() {
        banc.seances.semerUneLigneIllisible(CENTRE_A, new com.hemodialyse.backend.contexts.seance.domain.SeanceId(new UUID(5, 7)));

        assertThatThrownBy(() -> banc.changerLeJour().executer(CENTRE_A.valeur(), new UUID(5, 7), () -> JOUR))
                .isInstanceOf(SeanceIllisible.class);
    }

    @Test
    void should_not_apply_any_calendar_rule_or_the_past_days_lock() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        banc.changerLeJour().executer(CENTRE_A.valeur(), s.id().valeur(), () -> LocalDate.of(1900, 1, 1));
        assertThat(relue(s).jour()).isEqualTo(LocalDate.of(1900, 1, 1));
        banc.changerLeJour().executer(CENTRE_A.valeur(), s.id().valeur(), () -> LocalDate.of(9999, 12, 31));
        assertThat(relue(s).jour()).isEqualTo(LocalDate.of(9999, 12, 31));
    }
}
