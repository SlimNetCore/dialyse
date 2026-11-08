package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.FactureId;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.JOUR;
import static com.hemodialyse.backend.contexts.seance.application.Banc.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE6 : suppression d'une séance, ports en mémoire (ADR 0093) : tout ou rien, ordre des gardes du legacy, facture rattachée. */
class SupprimerUneSeanceTest {

    private static final FactureId FACTURE = new FactureId(new UUID(8, 1));
    private static final String MOTIF = "Seance saisie en double";

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

    private Seance semerAvecFacture(int n, StatutDeSeance statut) {
        var s = Seance.restituer(new SeanceId(new UUID(5, n)), CENTRE_A, patient, JOUR, statut, MAINTENANT, null, null, null, null, FACTURE);
        banc.seances.semer(s);
        return s;
    }

    private void supprimer(Seance s, String motif) {
        banc.supprimer().executer(CENTRE_A.valeur(), s.id().valeur(), motif);
    }

    @Test
    void should_cancel_the_consumptions_erase_the_entries_remove_the_session_then_journal_it_in_that_order() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        var suppression = banc.supprimer().executer(CENTRE_A.valeur(), s.id().valeur(), MOTIF);

        assertThat(banc.etapesDeLaSuppression).containsExactly("consommations:u-admin", "saisies", "suppression", "journal");
        assertThat(banc.seances.trouver(CENTRE_A, s.id())).isEmpty();
        assertThat(banc.journal).containsExactly(suppression);
        assertThat(suppression.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(suppression.auteur()).isEqualTo("u-admin");
        assertThat(suppression.supprimeLe()).isEqualTo(MAINTENANT);
        assertThat(suppression.id()).isEqualTo(new UUID(9, 100));
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
    }

    @Test
    void should_broadcast_the_removal_after_the_unit_of_work_and_only_then() {
        var s = semer(1, StatutDeSeance.SIGNEE);

        banc.supprimer().executer(CENTRE_A.valeur(), s.id().valeur(), MOTIF);

        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_SUPPRIMEE:" + s.id().valeur() + ":" + JOUR);
    }

    @Test
    void should_keep_the_trimmed_reason_in_the_journal() {
        var s = semer(1, StatutDeSeance.CREE);

        supprimer(s, "   Doublon   ");

        assertThat(banc.journal.get(0).motif().valeur()).isEqualTo("Doublon");
    }

    @Test
    void should_remove_a_session_whatever_its_day_signatures_and_patient() {
        var orpheline = Seance.restituer(new SeanceId(new UUID(5, 1)), CENTRE_A, new PatientId(new UUID(7, 999)), JOUR.minusDays(30),
                StatutDeSeance.SIGNEE, MAINTENANT, null, new Seance.Jalons(MAINTENANT, MAINTENANT, MAINTENANT, null));
        banc.seances.semer(orpheline);

        supprimer(orpheline, MOTIF);

        assertThat(banc.seances.trouver(CENTRE_A, orpheline.id())).isEmpty();
        assertThat(banc.journal).hasSize(1);
    }

    @Test
    void should_refuse_an_unknown_session_or_one_of_another_centre_with_a_422_not_found_before_the_reason() {
        var autre = banc.ligne(CENTRE_B, banc.patient(CENTRE_B, 2, "P002", "A2"), 2, StatutDeSeance.CREE);
        banc.seances.semer(autre);

        for (UUID inconnue : new UUID[]{new UUID(5, 99), autre.id().valeur()}) {
            assertThatThrownBy(() -> banc.supprimer().executer(CENTRE_A.valeur(), inconnue, ""))
                    .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                        assertThat(e.motif().code()).isEqualTo("SEANCE_INTROUVABLE");
                        assertThat(e.getMessage()).isEqualTo("Séance introuvable");
                    });
        }
        assertThat(banc.seances.trouver(CENTRE_B, autre.id())).isPresent();
    }

    @Test
    void should_check_the_reason_before_the_billed_status() {
        var facturee = semer(1, StatutDeSeance.FACTUREE);

        assertThatThrownBy(() -> supprimer(facturee, ""))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_SUPPRESSION_MOTIF_INVALIDE"));
        assertThatThrownBy(() -> supprimer(facturee, MOTIF))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_SUPPRIMABLE"));
        assertThat(banc.etapesDeLaSuppression).isEmpty();
    }

    @Test
    void should_refuse_a_session_attached_to_an_invoice_whatever_its_status_and_touch_nothing() {
        for (StatutDeSeance statut : new StatutDeSeance[]{StatutDeSeance.CREE, StatutDeSeance.ABSENT, StatutDeSeance.VALIDEE,
                StatutDeSeance.SIGNEE}) {
            var s = semerAvecFacture(statut.ordinal() + 1, statut);

            assertThatThrownBy(() -> supprimer(s, MOTIF)).isInstanceOfSatisfying(SeanceRefusee.class,
                    e -> assertThat(e.motif().code()).isEqualTo("SEANCE_RATTACHEE_A_UNE_FACTURE_NON_SUPPRIMABLE"));

            assertThat(banc.seances.trouver(CENTRE_A, s.id()).orElseThrow().facture()).contains(FACTURE);
        }
        assertThat(banc.etapesDeLaSuppression).isEmpty();
        assertThat(banc.journal).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_refuse_a_foreign_centre_before_anything_else() {
        var s = semer(1, StatutDeSeance.CREE);

        assertThatThrownBy(() -> banc.supprimer().executer(CENTRE_B.valeur(), s.id().valeur(), MOTIF)).isInstanceOf(AccesRefuse.class);
        assertThat(banc.seances.verrousDeLigneDemandes).isZero();
    }

    @Test
    void should_relay_the_refusal_of_the_stock_and_remove_nothing() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        banc.refusDeLAnnulation = "PIECE_DEJA_RAPPROCHEE";

        assertThatThrownBy(() -> supprimer(s, MOTIF)).isInstanceOfSatisfying(ConsommationRefusee.class,
                e -> assertThat(e.code()).isEqualTo("PIECE_DEJA_RAPPROCHEE"));

        assertThat(banc.seances.trouver(CENTRE_A, s.id())).isPresent();
        assertThat(banc.journal).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_be_all_or_nothing_when_the_journal_fails() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        banc.leJournalEchoue = true;

        assertThatThrownBy(() -> supprimer(s, MOTIF)).isInstanceOf(IllegalStateException.class);

        assertThat(banc.seances.trouver(CENTRE_A, s.id())).isPresent();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_record_system_as_the_author_when_the_session_has_no_user() {
        var s = semer(1, StatutDeSeance.CREE);
        banc.session.utilisateur = null;

        supprimer(s, MOTIF);

        assertThat(banc.journal.get(0).auteur()).isEqualTo("system");
        assertThat(banc.etapesDeLaSuppression.get(0)).isEqualTo("consommations:system");
    }

    @Test
    void should_report_an_unreadable_row_without_a_class_name() {
        banc.seances.semerUneLigneIllisible(CENTRE_A, new SeanceId(new UUID(5, 7)));

        assertThatThrownBy(() -> banc.supprimer().executer(CENTRE_A.valeur(), new UUID(5, 7), MOTIF)).isInstanceOf(SeanceIllisible.class);
        assertThat(banc.journal).isEmpty();
    }

    @Test
    void should_not_remove_twice() {
        var s = semer(1, StatutDeSeance.CREE);
        supprimer(s, MOTIF);

        assertThatThrownBy(() -> supprimer(s, MOTIF)).isInstanceOfSatisfying(SeanceRefusee.class,
                e -> assertThat(e.motif().code()).isEqualTo("SEANCE_INTROUVABLE"));
        assertThat(banc.journal).hasSize(1);
    }
}
