package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SE6 : changer le jour d'une séance et la supprimer (ADR 0093). Matrice invariant -> test : seule la séance facturée refuse le
 * changement de jour (KB-SEA-54 et 55 reproduits : aucune autre règle) ; la suppression refuse le statut facturé ET la facture rattachée
 * (KB-SEA-50 corrigé), le motif est contrôlé avant le statut (KB-SEA-52 reproduit).
 */
class JourEtSuppressionDeLaSeanceTest {

    private static final SeanceId ID = new SeanceId(new UUID(0, 1));
    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    private static final Instant T0 = Instant.parse("2026-06-15T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-16T09:00:00Z");
    private static final FactureId FACTURE = new FactureId(new UUID(8, 1));
    private static final MotifDeSuppression MOTIF = new MotifDeSuppression("Séance saisie en double");

    private static Seance enStatut(StatutDeSeance statut) {
        return Seance.restituer(ID, CENTRE, PATIENT, JOUR, statut, T0, null, null);
    }

    private static Seance avecFacture(StatutDeSeance statut) {
        return Seance.restituer(ID, CENTRE, PATIENT, JOUR, statut, T0, null, null, null, null, FACTURE);
    }

    // ---- changer le jour

    @ParameterizedTest
    @EnumSource(value = StatutDeSeance.class, names = {"CREE", "ABSENT", "VALIDEE", "SIGNEE"})
    void should_change_the_day_of_any_session_that_is_not_billed(StatutDeSeance statut) {
        var seance = enStatut(statut);

        seance.changerDeJour(JOUR.plusDays(2));

        assertThat(seance.jour()).isEqualTo(JOUR.plusDays(2));
        assertThat(seance.statut()).isEqualTo(statut);
    }

    @Test
    void should_refuse_to_change_the_day_of_a_billed_session() {
        var seance = enStatut(StatutDeSeance.FACTUREE);

        assertThatThrownBy(() -> seance.changerDeJour(JOUR.plusDays(1)))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE");
                    assertThat(e.getMessage()).isEqualTo("La seance facturee ne peut plus etre modifiee");
                });
        assertThat(seance.jour()).isEqualTo(JOUR);
    }

    @Test
    void should_keep_the_signatures_the_place_the_forfait_and_the_invoice_when_the_day_changes() {
        var signee = Seance.restituer(ID, CENTRE, PATIENT, JOUR, StatutDeSeance.SIGNEE, T0, null,
                new Seance.Jalons(T0, T0, T0, null), new Seance.Traces("inf", "med", null, null), null, FACTURE);

        signee.changerDeJour(JOUR.minusDays(3));

        assertThat(signee.jalons()).isEqualTo(new Seance.Jalons(T0, T0, T0, null));
        assertThat(signee.traces().infirmier()).isEqualTo("inf");
        assertThat(signee.traces().medecin()).isEqualTo("med");
        assertThat(signee.facture()).contains(FACTURE);
    }

    @Test
    void should_accept_a_day_in_the_past_or_the_far_future_without_any_calendar_rule() {
        var seance = enStatut(StatutDeSeance.CREE);

        assertThatCode(() -> {
            seance.changerDeJour(LocalDate.of(1900, 1, 1));
            seance.changerDeJour(LocalDate.of(9999, 12, 31));
        }).doesNotThrowAnyException();
    }

    @Test
    void should_require_a_day() {
        assertThatThrownBy(() -> enStatut(StatutDeSeance.CREE).changerDeJour(null)).isInstanceOf(SaisieInvalide.class);
    }

    // ---- motif de suppression

    @Test
    void should_trim_the_reason_before_measuring_and_keeping_it() {
        assertThat(new MotifDeSuppression("  Doublon  ").valeur()).isEqualTo("Doublon");
        assertThat(new MotifDeSuppression("a".repeat(500)).valeur()).hasSize(500);
        assertThat(new MotifDeSuppression("  abcde  ").valeur()).isEqualTo("abcde");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "    ", "abcd", " abcd "})
    void should_refuse_a_reason_shorter_than_five_characters(String motif) {
        assertThatThrownBy(() -> new MotifDeSuppression(motif)).isInstanceOfSatisfying(SeanceRefusee.class, e -> {
            assertThat(e.motif().code()).isEqualTo("SEANCE_SUPPRESSION_MOTIF_INVALIDE");
            assertThat(e.getMessage()).isEqualTo("Le motif de suppression doit compter de 5 à 500 caractères");
        });
    }

    @Test
    void should_refuse_an_absent_or_too_long_reason() {
        assertThatThrownBy(() -> new MotifDeSuppression(null)).isInstanceOf(SeanceRefusee.class);
        assertThatThrownBy(() -> new MotifDeSuppression("a".repeat(501))).isInstanceOf(SeanceRefusee.class);
    }

    // ---- supprimer

    @ParameterizedTest
    @EnumSource(value = StatutDeSeance.class, names = {"CREE", "ABSENT", "VALIDEE", "SIGNEE"})
    void should_prepare_the_removal_of_a_session_that_is_not_billed_and_keep_its_trace(StatutDeSeance statut) {
        var id = new UUID(9, 1);

        var suppression = SuppressionDeSeance.de(id, enStatut(statut), MOTIF, "u-admin", T1);

        assertThat(suppression.id()).isEqualTo(id);
        assertThat(suppression.centre()).isEqualTo(CENTRE);
        assertThat(suppression.seance()).isEqualTo(ID);
        assertThat(suppression.patient()).isEqualTo(PATIENT);
        assertThat(suppression.jour()).isEqualTo(JOUR);
        assertThat(suppression.statut()).isEqualTo(statut);
        assertThat(suppression.motif().valeur()).isEqualTo("Séance saisie en double");
        assertThat(suppression.auteur()).isEqualTo("u-admin");
        assertThat(suppression.supprimeLe()).isEqualTo(T1);
    }

    @Test
    void should_refuse_to_remove_a_billed_session() {
        assertThatThrownBy(() -> SuppressionDeSeance.de(new UUID(9, 1), enStatut(StatutDeSeance.FACTUREE), MOTIF, "u-admin", T1))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_SUPPRIMABLE");
                    assertThat(e.getMessage()).isEqualTo("Une séance facturée ne peut pas être supprimée : annulez d'abord la facture");
                });
    }

    @ParameterizedTest
    @EnumSource(value = StatutDeSeance.class, names = {"CREE", "ABSENT", "VALIDEE", "SIGNEE"})
    void should_refuse_to_remove_a_session_attached_to_an_invoice_whatever_its_status(StatutDeSeance statut) {
        assertThatThrownBy(() -> SuppressionDeSeance.de(new UUID(9, 1), avecFacture(statut), MOTIF, "u-admin", T1))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("SEANCE_RATTACHEE_A_UNE_FACTURE_NON_SUPPRIMABLE");
                    assertThat(e.getMessage()).contains("facture");
                });
    }

    @Test
    void should_report_the_billed_status_before_the_invoice_attachment() {
        assertThatThrownBy(() -> SuppressionDeSeance.de(new UUID(9, 1), avecFacture(StatutDeSeance.FACTUREE), MOTIF, "u-admin", T1))
                .isInstanceOfSatisfying(SeanceRefusee.class,
                        e -> assertThat(e.motif()).isEqualTo(MotifDeRefus.SEANCE_FACTUREE_NON_SUPPRIMABLE));
    }

    @Test
    void should_remove_a_session_whatever_its_day_and_signatures() {
        var passee = Seance.restituer(ID, CENTRE, PATIENT, JOUR.minusDays(30), StatutDeSeance.SIGNEE, T0, null,
                new Seance.Jalons(T0, T0, T0, null));

        assertThatCode(() -> SuppressionDeSeance.de(new UUID(9, 1), passee, MOTIF, "u-admin", T1)).doesNotThrowAnyException();
    }

    @Test
    void should_record_system_when_the_session_gives_no_author() {
        assertThat(SuppressionDeSeance.de(new UUID(9, 1), enStatut(StatutDeSeance.CREE), MOTIF, null, T1).auteur()).isEqualTo("system");
        assertThat(SuppressionDeSeance.de(new UUID(9, 1), enStatut(StatutDeSeance.CREE), MOTIF, "  ", T1).auteur()).isEqualTo("system");
    }
}
