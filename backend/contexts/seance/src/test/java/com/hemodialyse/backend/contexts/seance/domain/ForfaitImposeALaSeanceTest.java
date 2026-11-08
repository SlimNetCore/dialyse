package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE5 : imposer un forfait du référentiel à une séance (surcharge), matrice des statuts et auteur du legacy (KB-SEA-41 à 45). */
class ForfaitImposeALaSeanceTest {

    private static final SeanceId ID = new SeanceId(new UUID(0, 1));
    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    private static final Instant T0 = Instant.parse("2026-06-15T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-15T10:00:00Z");
    private static final ForfaitDuCentre F1 = new ForfaitDuCentre(new ForfaitId(new UUID(6, 1)), "F1", "Forfait 1", new BigDecimal("100.00"));
    private static final ForfaitDuCentre F2 = new ForfaitDuCentre(new ForfaitId(new UUID(6, 2)), "F2", "Forfait 2", new BigDecimal("180.50"));
    private static final AuteurDuForfaitImpose COMPTA = new AuteurDuForfaitImpose("compta-01");

    private static Seance enStatut(StatutDeSeance statut) {
        return Seance.restituer(ID, CENTRE, PATIENT, JOUR, statut, T0, null, null);
    }

    @Test
    void should_copy_the_code_the_name_and_the_price_of_the_forfait_with_the_author_and_the_instant() {
        var seance = enStatut(StatutDeSeance.CREE);

        seance.imposerUnForfait(F1, T1, COMPTA);

        var impose = seance.forfaitImpose().orElseThrow();
        assertThat(impose.forfait()).isEqualTo(F1.id());
        assertThat(impose.code()).isEqualTo("F1");
        assertThat(impose.nom()).isEqualTo("Forfait 1");
        assertThat(impose.prix()).isEqualByComparingTo("100.00");
        assertThat(impose.imposeLe()).isEqualTo(T1);
        assertThat(impose.auteur()).isEqualTo("compta-01");
        assertThat(seance.statut()).isEqualTo(StatutDeSeance.CREE);
    }

    @Test
    void should_replace_everything_without_history_when_a_forfait_is_imposed_again() {
        var seance = enStatut(StatutDeSeance.VALIDEE);
        seance.imposerUnForfait(F1, T1, COMPTA);

        seance.imposerUnForfait(F2, T2, new AuteurDuForfaitImpose("compta-02"));

        var impose = seance.forfaitImpose().orElseThrow();
        assertThat(impose).isEqualTo(new ForfaitImpose(F2.id(), "F2", "Forfait 2", new BigDecimal("180.50"), T2, "compta-02"));
    }

    @Test
    void should_renew_the_instant_even_for_the_same_forfait() {
        var seance = enStatut(StatutDeSeance.CREE);
        seance.imposerUnForfait(F2, T1, COMPTA);

        seance.imposerUnForfait(F2, T2, new AuteurDuForfaitImpose("compta-03"));

        assertThat(seance.forfaitImpose().orElseThrow().imposeLe()).isEqualTo(T2);
        assertThat(seance.forfaitImpose().orElseThrow().auteur()).isEqualTo("compta-03");
    }

    @Test
    void should_accept_a_forfait_without_price_or_with_a_zero_price_and_copy_it_as_is() {
        var sansPrix = enStatut(StatutDeSeance.CREE);
        var prixNul = enStatut(StatutDeSeance.CREE);

        sansPrix.imposerUnForfait(new ForfaitDuCentre(F1.id(), "F1", "Forfait 1", null), T1, COMPTA);
        prixNul.imposerUnForfait(new ForfaitDuCentre(F1.id(), "F1", "Forfait 1", new BigDecimal("0.00")), T1, COMPTA);

        assertThat(sansPrix.forfaitImpose().orElseThrow().prix()).isNull();
        assertThat(prixNul.forfaitImpose().orElseThrow().prix()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void should_accept_every_status_but_the_invoiced_one_even_an_absent_session() {
        for (StatutDeSeance statut : StatutDeSeance.values()) {
            if (statut == StatutDeSeance.FACTUREE) continue;
            var seance = enStatut(statut);

            seance.imposerUnForfait(F1, T1, COMPTA);

            assertThat(seance.forfaitImpose()).as(statut.name()).isPresent();
            assertThat(seance.statut()).isEqualTo(statut);
        }
    }

    @Test
    void should_refuse_an_invoiced_session_and_keep_the_forfait_imposed_before_the_invoicing() {
        var seance = Seance.restituer(ID, CENTRE, PATIENT, JOUR, StatutDeSeance.FACTUREE, T0, null, null, null,
                new ForfaitImpose(F1.id(), "F1", "Forfait 1", new BigDecimal("100.00"), T1, "compta-01"));

        assertThatThrownBy(() -> seance.imposerUnForfait(F2, T2, COMPTA)).isInstanceOfSatisfying(SeanceRefusee.class,
                e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"));
        assertThat(seance.forfaitImpose().orElseThrow().code()).isEqualTo("F1");
    }

    @Test
    void should_have_no_forfait_imposed_by_default_and_restore_one_written_by_the_billing_without_code_or_author() {
        assertThat(enStatut(StatutDeSeance.CREE).forfaitImpose()).isEmpty();
        var seance = Seance.restituer(ID, CENTRE, PATIENT, JOUR, StatutDeSeance.VALIDEE, T0, null, null, null,
                new ForfaitImpose(F1.id(), null, "Forfait 1", null, null, null));

        assertThat(seance.forfaitImpose().orElseThrow().code()).isNull();
        assertThat(seance.forfaitImpose().orElseThrow().auteur()).isNull();
    }

    @Test
    void should_trim_the_author_and_refuse_a_blank_one_or_one_over_one_hundred_characters() {
        assertThat(new AuteurDuForfaitImpose("   compta-02   ").valeur()).isEqualTo("compta-02");
        assertThat(new AuteurDuForfaitImpose("x".repeat(100)).valeur()).hasSize(100);
        assertThatThrownBy(() -> new AuteurDuForfaitImpose(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new AuteurDuForfaitImpose("")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new AuteurDuForfaitImpose("     ")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new AuteurDuForfaitImpose("y".repeat(101))).isInstanceOf(SaisieInvalide.class)
                .hasMessageNotContaining("com.hemodialyse");
    }

    @Test
    void should_require_a_forfait_identity() {
        assertThatThrownBy(() -> new ForfaitId(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ForfaitDuCentre(null, "F", "n", null)).isInstanceOf(SaisieInvalide.class);
    }
}
