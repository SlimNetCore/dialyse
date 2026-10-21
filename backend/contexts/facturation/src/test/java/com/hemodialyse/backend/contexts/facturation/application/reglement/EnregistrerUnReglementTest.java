package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.doubles.PublicationDesReglementsEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.ReglementsEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.ModeDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.RemboursementSuperieurAuTotalRegle;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.TypeDeSolde;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FactureReglementRecordedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnregistrerUnReglementTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99995900-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("99995900-0000-0000-0000-0000000000b1"));
    private static final UUID FACTURE = UUID.fromString("99995900-0000-0000-0000-0000000f0001");
    private static final UUID UTILISATEUR = UUID.fromString("99995900-0000-0000-0000-00000000c001");
    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 10, 21);

    private final ReglementsEnMemoire reglements = new ReglementsEnMemoire();
    private final PublicationDesReglementsEnMemoire publication = new PublicationDesReglementsEnMemoire();
    private final Deque<UUID> identifiants = new ArrayDeque<>();
    private EnregistrerUnReglement enregistrer;

    @BeforeEach
    void preparer() {
        reglements.poserLaFacture(CENTRE, FACTURE, "FAC-0001", LocalDate.of(2026, 1, 31), new BigDecimal("1000.00"));
        for (int i = 1; i <= 20; i++) {
            identifiants.add(UUID.fromString(String.format("abcdef%02d-0000-0000-0000-000000000000", i)));
        }
        enregistrer = new EnregistrerUnReglement(reglements, reglements, publication, new UniteDeTravailDirecte(),
                Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC), identifiants::pop);
    }

    private static DemandeDeReglement demande(String montant) {
        return new DemandeDeReglement(new BigDecimal(montant), LocalDate.of(2026, 2, 1), null, null, null);
    }

    @Test
    void should_record_a_partial_payment_and_answer_the_situation_of_the_invoice() {
        LigneDeSituation ligne = enregistrer.enregistrer(CENTRE, FACTURE, demande("400.00"), UTILISATEUR);

        assertThat(ligne.etat()).isEqualTo(EtatDeReglement.PARTIELLEMENT_REGLEE);
        assertThat(ligne.montantRegle()).isEqualByComparingTo("400.00");
        assertThat(ligne.reste()).isEqualByComparingTo("600.00");
        assertThat(ligne.paymentCount()).isEqualTo(1);
        assertThat(ligne.latestCodeReglement()).isEqualTo("REG-ABCDEF01");
    }

    @Test
    void should_record_the_session_user_as_author_and_system_when_unknown() {
        enregistrer.enregistrer(CENTRE, FACTURE, demande("100.00"), UTILISATEUR);
        enregistrer.enregistrer(CENTRE, FACTURE, demande("100.00"), null);

        assertThat(reglements.enregistres()).extracting("auteur").containsExactly(UTILISATEUR.toString(), "system");
    }

    @Test
    void should_default_the_date_to_the_day_of_execution() {
        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(BigDecimal.TEN, null, null, null, null), UTILISATEUR);

        assertThat(reglements.enregistres().getFirst().date()).isEqualTo(AUJOURD_HUI);
    }

    @Test
    void should_not_control_the_date() {
        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(BigDecimal.TEN, LocalDate.of(1999, 1, 1), null, null, null),
                UTILISATEUR);
        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(BigDecimal.TEN, LocalDate.of(2999, 1, 1), null, null, null),
                UTILISATEUR);

        assertThat(reglements.enregistres()).hasSize(2);
    }

    @Test
    void should_settle_the_invoice_then_accept_an_overpayment() {
        assertThat(enregistrer.enregistrer(CENTRE, FACTURE, demande("1000.00"), UTILISATEUR).soldeType()).isEqualTo(TypeDeSolde.REGLE);

        LigneDeSituation trop = enregistrer.enregistrer(CENTRE, FACTURE, demande("50.00"), UTILISATEUR);

        assertThat(trop.etat()).isEqualTo(EtatDeReglement.REGLEE);
        assertThat(trop.soldeType()).isEqualTo(TypeDeSolde.TROP_PERCU);
        assertThat(trop.tropPercu()).isEqualByComparingTo("50.00");
    }

    @Test
    void should_publish_the_event_with_the_unrounded_signed_amount_the_author_the_mode_and_the_identity() {
        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(new BigDecimal("500.005"), LocalDate.of(2026, 2, 1),
                ModeDeReglement.ESPECES, "RECU-9", null), UTILISATEUR);

        assertThat(publication.publies()).containsExactly(new FactureReglementRecordedEvent(
                reglements.enregistres().getFirst().id(), FACTURE, CENTRE.valeur(), new BigDecimal("500.005"),
                LocalDate.of(2026, 2, 1), UTILISATEUR.toString(), "ESPECES"));
        assertThat(reglements.enregistres().getFirst().montant()).isEqualByComparingTo("500.01");
    }

    @Test
    void should_publish_a_refund_with_its_negative_amount_and_no_mode() {
        enregistrer.enregistrer(CENTRE, FACTURE, demande("300.00"), UTILISATEUR);

        enregistrer.enregistrer(CENTRE, FACTURE, demande("-100.00"), UTILISATEUR);

        assertThat(publication.publies().getLast().montant()).isEqualByComparingTo("-100.00");
        assertThat(publication.publies().getLast().mode()).isNull();
    }

    @Test
    void should_refuse_a_refund_above_the_total_paid_without_recording_nor_publishing() {
        enregistrer.enregistrer(CENTRE, FACTURE, demande("100.00"), UTILISATEUR);

        assertThatThrownBy(() -> enregistrer.enregistrer(CENTRE, FACTURE, demande("-100.01"), UTILISATEUR))
                .isInstanceOf(RemboursementSuperieurAuTotalRegle.class);

        assertThat(reglements.enregistres()).hasSize(1);
        assertThat(publication.publies()).hasSize(1);
    }

    @Test
    void should_refuse_an_unknown_invoice_and_an_invoice_of_another_centre() {
        assertThatThrownBy(() -> enregistrer.enregistrer(CENTRE, UUID.randomUUID(), demande("10"), UTILISATEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Facture introuvable pour le centre courant");
        assertThatThrownBy(() -> enregistrer.enregistrer(AUTRE_CENTRE, FACTURE, demande("10"), UTILISATEUR))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Facture introuvable pour le centre courant");
        assertThat(publication.publies()).isEmpty();
    }

    @Test
    void should_refuse_an_amount_that_rounds_to_zero_without_publishing() {
        assertThatThrownBy(() -> enregistrer.enregistrer(CENTRE, FACTURE, demande("0.004"), UTILISATEUR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reglements.enregistres()).isEmpty();
        assertThat(publication.publies()).isEmpty();
    }

    @Test
    void should_refuse_a_null_amount_when_the_request_is_built() {
        assertThatThrownBy(() -> new DemandeDeReglement(BigDecimal.ZERO, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemandeDeReglement(null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_create_a_duplicate_without_key_as_before() {
        enregistrer.enregistrer(CENTRE, FACTURE, demande("100.00"), UTILISATEUR);
        enregistrer.enregistrer(CENTRE, FACTURE, demande("100.00"), UTILISATEUR);

        assertThat(reglements.enregistres()).hasSize(2);
        assertThat(publication.publies()).hasSize(2);
    }

    @Test
    void should_answer_the_current_situation_without_duplicate_nor_event_when_the_key_was_already_used() {
        DemandeDeReglement avecCle = new DemandeDeReglement(new BigDecimal("100.00"), LocalDate.of(2026, 2, 1), null, null, "envoi-1");
        LigneDeSituation premiere = enregistrer.enregistrer(CENTRE, FACTURE, avecCle, UTILISATEUR);

        LigneDeSituation seconde = enregistrer.enregistrer(CENTRE, FACTURE, avecCle, UTILISATEUR);

        assertThat(seconde).isEqualTo(premiere);
        assertThat(reglements.enregistres()).hasSize(1);
        assertThat(publication.publies()).hasSize(1);
    }

    @Test
    void should_ignore_the_content_of_a_replay_with_the_same_key() {
        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(new BigDecimal("100.00"), null, null, null, " envoi-1 "),
                UTILISATEUR);

        LigneDeSituation seconde = enregistrer.enregistrer(CENTRE, FACTURE,
                new DemandeDeReglement(new BigDecimal("900.00"), null, null, null, "envoi-1"), UTILISATEUR);

        assertThat(seconde.montantRegle()).isEqualByComparingTo("100.00");
        assertThat(reglements.enregistres()).hasSize(1);
    }

    @Test
    void should_scope_the_key_to_the_invoice_and_treat_a_blank_key_as_absent() {
        UUID autre = UUID.randomUUID();
        reglements.poserLaFacture(CENTRE, autre, "FAC-0002", LocalDate.of(2026, 1, 31), new BigDecimal("500.00"));

        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(BigDecimal.TEN, null, null, null, "k"), UTILISATEUR);
        enregistrer.enregistrer(CENTRE, autre, new DemandeDeReglement(BigDecimal.TEN, null, null, null, "k"), UTILISATEUR);
        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(BigDecimal.TEN, null, null, null, "  "), UTILISATEUR);
        enregistrer.enregistrer(CENTRE, FACTURE, new DemandeDeReglement(BigDecimal.TEN, null, null, null, "  "), UTILISATEUR);

        assertThat(reglements.enregistres()).hasSize(4);
    }
}
