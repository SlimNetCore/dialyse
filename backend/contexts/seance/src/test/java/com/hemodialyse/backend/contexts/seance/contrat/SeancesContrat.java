package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.DerogationHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.AuteurDuForfaitImpose;
import com.hemodialyse.backend.contexts.seance.domain.FactureId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitImpose;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.NombreDeSeancesRecentes;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.Signataire;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du port {@link Seances} : le même jeu de tests est rejoué sur le double en mémoire et sur la base (H2 et PostgreSQL 16), ce qui
 * garantit la fidélité du double.
 */
public abstract class SeancesContrat {

    protected static final CentreId CENTRE_A = new CentreId(new UUID(0, 0xA));
    protected static final CentreId CENTRE_B = new CentreId(new UUID(0, 0xB));
    protected static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    protected static final PatientId AUTRE_PATIENT = new PatientId(new UUID(1, 2));
    protected static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    protected static final Instant T0 = Instant.parse("2026-06-15T08:00:00Z");

    /** Banc de données du port testé : sème des lignes telles que le legacy les a écrites. */
    public interface Banc {
        Seances seances();

        /** Une ligne existante, avec son statut et ses jalons (validation, signatures, régularisation). */
        void semer(Seance seance);

        /** Une ligne dont le statut n'est pas un statut connu (reliquat). */
        void semerUneLigneIllisible(CentreId centre, SeanceId id);
    }

    protected abstract Banc nouveauBanc();

    protected static SeanceId s(int n) {
        return new SeanceId(new UUID(2, n));
    }

    private static Seance programmee(int n, CentreId centre, PatientId patient, LocalDate jour, Instant creeLe) {
        return Seance.creer(s(n), centre, patient, jour, creeLe);
    }

    @Test
    void should_find_a_session_added_as_it_was_created() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        assertThat(lue.id()).isEqualTo(s(1));
        assertThat(lue.centre()).isEqualTo(CENTRE_A);
        assertThat(lue.patient()).isEqualTo(PATIENT);
        assertThat(lue.jour()).isEqualTo(JOUR);
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(lue.creeLe()).isEqualTo(T0);
        assertThat(lue.horsPlanning()).isFalse();
        assertThat(lue.jalons()).isEqualTo(Seance.Jalons.AUCUN);
    }

    @Test
    void should_keep_the_motif_and_the_precision_of_a_session_outside_the_planning() {
        var banc = nouveauBanc();
        var derogation = new DerogationHorsPlanning(MotifHorsPlanning.AUTRE, "Panne du poste", true);
        banc.seances().ajouter(Seance.creerHorsPlanning(s(1), CENTRE_A, PATIENT, JOUR, T0, derogation));

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        assertThat(lue.horsPlanning()).isTrue();
        assertThat(lue.derogation().orElseThrow().motif()).isEqualTo(MotifHorsPlanning.AUTRE);
        assertThat(lue.derogation().orElseThrow().precision()).isEqualTo("Panne du poste");
    }

    @Test
    void should_never_give_a_session_of_another_centre() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().trouver(CENTRE_B, s(1))).isEmpty();
        assertThat(banc.seances().duJour(CENTRE_B, PATIENT, JOUR)).isEmpty();
    }

    @Test
    void should_give_the_latest_sessions_of_the_patient_strictly_before_the_day_newest_first_whatever_their_status() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR.minusDays(1), T0));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR.minusDays(3), T0));
        banc.seances().ajouter(programmee(3, CENTRE_A, PATIENT, JOUR.minusDays(2), T0));
        banc.seances().ajouter(programmee(4, CENTRE_A, PATIENT, JOUR, T0));
        banc.seances().ajouter(programmee(5, CENTRE_A, PATIENT, JOUR.plusDays(1), T0));
        banc.seances().ajouter(programmee(6, CENTRE_A, AUTRE_PATIENT, JOUR.minusDays(1), T0));
        banc.seances().ajouter(programmee(7, CENTRE_B, PATIENT, JOUR.minusDays(1), T0));

        var dernieres = banc.seances().dernieres(CENTRE_A, PATIENT, JOUR, NombreDeSeancesRecentes.demande(10));

        assertThat(dernieres).extracting(Seance::id).containsExactly(s(1), s(3), s(2));
        assertThat(dernieres).extracting(Seance::statut).containsOnly(StatutDeSeance.CREE);
    }

    @Test
    void should_stop_at_the_requested_number_and_give_nothing_for_an_unknown_patient_or_another_centre() {
        var banc = nouveauBanc();
        for (int n = 1; n <= 4; n++) banc.seances().ajouter(programmee(n, CENTRE_A, PATIENT, JOUR.minusDays(n), T0));

        assertThat(banc.seances().dernieres(CENTRE_A, PATIENT, JOUR, NombreDeSeancesRecentes.demande(2))).extracting(Seance::id)
                .containsExactly(s(1), s(2));
        assertThat(banc.seances().dernieres(CENTRE_A, AUTRE_PATIENT, JOUR, NombreDeSeancesRecentes.demande(3))).isEmpty();
        assertThat(banc.seances().dernieres(CENTRE_B, PATIENT, JOUR, NombreDeSeancesRecentes.demande(3))).isEmpty();
    }

    @Test
    void should_order_the_sessions_of_the_same_day_by_the_latest_creation_first_then_by_identifier() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR.minusDays(1), T0));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR.minusDays(1), T0.plusSeconds(60)));
        banc.seances().ajouter(programmee(3, CENTRE_A, PATIENT, JOUR.minusDays(1), T0));

        assertThat(banc.seances().dernieres(CENTRE_A, PATIENT, JOUR, NombreDeSeancesRecentes.demande(10))).extracting(Seance::id)
                .containsExactly(s(2), s(1), s(3));
    }

    @Test
    void should_find_nothing_for_an_unknown_session() {
        assertThat(nouveauBanc().seances().trouver(CENTRE_A, s(99))).isEmpty();
    }

    @Test
    void should_give_the_sessions_of_the_patient_for_the_day_oldest_first() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0.plusSeconds(60)));
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        banc.seances().ajouter(programmee(3, CENTRE_A, PATIENT, JOUR.plusDays(1), T0));
        banc.seances().ajouter(programmee(4, CENTRE_A, AUTRE_PATIENT, JOUR, T0));

        var duJour = banc.seances().duJour(CENTRE_A, PATIENT, JOUR);

        assertThat(duJour).extracting(Seance::id).containsExactly(s(1), s(2));
    }

    @Test
    void should_allow_several_sessions_for_the_same_patient_and_day() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().duJour(CENTRE_A, PATIENT, JOUR)).hasSize(2);
    }

    @Test
    void should_give_a_session_without_creation_time_last() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.VALIDEE, null, null, null));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().duJour(CENTRE_A, PATIENT, JOUR)).extracting(Seance::id).containsExactly(s(2), s(1));
    }

    @Test
    void should_restore_the_milestones_written_by_the_legacy() {
        var banc = nouveauBanc();
        var jalons = new Seance.Jalons(T0.plusSeconds(10), T0.plusSeconds(11), T0.plusSeconds(12), T0.plusSeconds(13));
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.SIGNEE, T0, null, jalons));

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        assertThat(lue.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(lue.jalons()).isEqualTo(jalons);
        assertThat(lue.attendLaValidation()).isFalse();
    }

    @Test
    void should_read_every_status_of_the_legacy_including_the_historical_absent() {
        var banc = nouveauBanc();
        int n = 1;
        for (StatutDeSeance statut : StatutDeSeance.values()) {
            banc.semer(Seance.restituer(s(n), CENTRE_A, PATIENT, JOUR.plusDays(n), statut, T0, null, null));
            assertThat(banc.seances().trouver(CENTRE_A, s(n)).orElseThrow().statut()).isEqualTo(statut);
            n++;
        }
    }

    @Test
    void should_report_an_unreadable_line_by_its_identifier_without_a_technical_class_name() {
        var banc = nouveauBanc();
        banc.semerUneLigneIllisible(CENTRE_A, s(7));

        assertThatThrownBy(() -> banc.seances().trouver(CENTRE_A, s(7)))
                .isInstanceOfSatisfying(SeanceIllisible.class, e -> {
                    assertThat(e.seance()).isEqualTo(s(7));
                    assertThat(e.getMessage()).doesNotContain("com.hemodialyse").doesNotContain("Exception");
                });
    }

    @Test
    void should_lock_the_day_without_disturbing_the_reads() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));

        assertThatCode(() -> banc.seances().verrouillerLeJour(CENTRE_A, PATIENT, JOUR)).doesNotThrowAnyException();
        assertThat(banc.seances().duJour(CENTRE_A, PATIENT, JOUR)).hasSize(1);
    }

    private static final Instant T1 = Instant.parse("2026-06-15T09:30:00Z");
    private static final PlaceDeSeance PLACE = new PlaceDeSeance(new UUID(3, 1), new UUID(4, 1), new UUID(5, 1));

    @Test
    void should_persist_a_validation_with_the_signatory_and_the_memorized_place() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        var seance = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        seance.validerParLInfirmier(T1, new Signataire("inf-01"));
        seance.memoriserPlace(PLACE);

        banc.seances().enregistrer(seance);

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(lue.jalons().valideeLe()).isEqualTo(T1);
        assertThat(lue.jalons().signeeParLInfirmierLe()).isEqualTo(T1);
        assertThat(lue.traces().infirmier()).isEqualTo("inf-01");
        assertThat(lue.traces().place()).isEqualTo(PLACE);
    }

    @Test
    void should_persist_the_doctor_signature_and_then_the_unlock_of_a_created_session() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.VALIDEE, T0, null, new Seance.Jalons(T0, T0, null, null),
                new Seance.Traces("inf-initiale", null, null, null)));
        banc.semer(programmee(2, CENTRE_A, PATIENT, JOUR.minusDays(3), T0));

        var validee = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        validee.signerParLeMedecin(T1, new Signataire("med-01"));
        banc.seances().enregistrer(validee);
        var creee = banc.seances().trouverPourModifier(CENTRE_A, s(2)).orElseThrow();
        creee.deverrouillerPourRegularisation(T1, new Signataire("u-admin"), JOUR);
        banc.seances().enregistrer(creee);

        var signee = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        assertThat(signee.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(signee.jalons().signeeParLeMedecinLe()).isEqualTo(T1);
        assertThat(signee.traces().medecin()).isEqualTo("med-01");
        assertThat(signee.traces().infirmier()).isEqualTo("inf-initiale");
        var deverrouillee = banc.seances().trouver(CENTRE_A, s(2)).orElseThrow();
        assertThat(deverrouillee.statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(deverrouillee.estDeverrouilleePourRegularisation()).isTrue();
        assertThat(deverrouillee.traces().deverrouilleePar()).isEqualTo("u-admin");
    }

    @Test
    void should_read_a_memorized_place_without_generator() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.VALIDEE, T0, null, new Seance.Jalons(T0, T0, null, null),
                new Seance.Traces("inf", null, null, new PlaceDeSeance(new UUID(3, 1), new UUID(4, 1), null))));

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        assertThat(lue.traces().place()).isEqualTo(new PlaceDeSeance(new UUID(3, 1), new UUID(4, 1), null));
    }

    @Test
    void should_not_persist_a_change_that_was_never_recorded() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        var seance = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();

        seance.validerParLInfirmier(T1, new Signataire("inf-01"));

        assertThat(banc.seances().trouver(CENTRE_A, s(1)).orElseThrow().statut()).isEqualTo(StatutDeSeance.CREE);
    }

    @Test
    void should_lock_and_read_only_a_session_of_the_centre_and_report_an_unreadable_one() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        banc.semerUneLigneIllisible(CENTRE_A, s(7));

        assertThat(banc.seances().trouverPourModifier(CENTRE_A, s(1))).isPresent();
        assertThat(banc.seances().trouverPourModifier(CENTRE_B, s(1))).isEmpty();
        assertThat(banc.seances().trouverPourModifier(CENTRE_A, s(99))).isEmpty();
        assertThatThrownBy(() -> banc.seances().trouverPourModifier(CENTRE_A, s(7))).isInstanceOf(SeanceIllisible.class);
    }

    // ---- SE5 : forfait imposé

    private static final ForfaitDuCentre F1 = new ForfaitDuCentre(new ForfaitId(new UUID(6, 1)), "F1", "Forfait 1", new BigDecimal("250.50"));
    private static final AuteurDuForfaitImpose COMPTA = new AuteurDuForfaitImpose("compta-01");

    @Test
    void should_persist_a_forfait_imposed_with_its_snapshot_the_author_and_the_instant() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        var seance = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        seance.imposerUnForfait(F1, T1, COMPTA);

        banc.seances().enregistrerLeForfaitImpose(seance);

        var impose = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow().forfaitImpose().orElseThrow();
        assertThat(impose.forfait()).isEqualTo(F1.id());
        assertThat(impose.code()).isEqualTo("F1");
        assertThat(impose.nom()).isEqualTo("Forfait 1");
        assertThat(impose.prix()).isEqualByComparingTo("250.50");
        assertThat(impose.imposeLe()).isEqualTo(T1);
        assertThat(impose.auteur()).isEqualTo("compta-01");
    }

    @Test
    void should_replace_the_forfait_imposed_and_keep_a_missing_price_missing() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        var premiere = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        premiere.imposerUnForfait(F1, T1, COMPTA);
        banc.seances().enregistrerLeForfaitImpose(premiere);
        var seconde = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        seconde.imposerUnForfait(new ForfaitDuCentre(new ForfaitId(new UUID(6, 2)), "F2", "Forfait 2", null), T1.plusSeconds(5),
                new AuteurDuForfaitImpose("compta-02"));

        banc.seances().enregistrerLeForfaitImpose(seconde);

        var impose = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow().forfaitImpose().orElseThrow();
        assertThat(impose.code()).isEqualTo("F2");
        assertThat(impose.prix()).isNull();
        assertThat(impose.imposeLe()).isEqualTo(T1.plusSeconds(5));
        assertThat(impose.auteur()).isEqualTo("compta-02");
    }

    @Test
    void should_write_only_the_forfait_columns_and_never_undo_a_concurrent_change_of_the_status() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.VALIDEE, T0, null, new Seance.Jalons(T0, T0, null, null),
                new Seance.Traces("inf-01", null, null, null)));
        var lueAvant = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        var signee = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        signee.signerParLeMedecin(T1, new Signataire("med-01"));
        banc.seances().enregistrer(signee);

        lueAvant.imposerUnForfait(F1, T1, COMPTA);
        banc.seances().enregistrerLeForfaitImpose(lueAvant);

        var finale = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        assertThat(finale.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(finale.traces().medecin()).isEqualTo("med-01");
        assertThat(finale.forfaitImpose().orElseThrow().code()).isEqualTo("F1");
    }

    @Test
    void should_not_let_a_status_change_erase_the_forfait_imposed() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        var seance = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        seance.imposerUnForfait(F1, T1, COMPTA);
        banc.seances().enregistrerLeForfaitImpose(seance);

        var validee = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        validee.validerParLInfirmier(T1, new Signataire("inf-01"));
        banc.seances().enregistrer(validee);

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(lue.forfaitImpose().orElseThrow().code()).isEqualTo("F1");
    }

    @Test
    void should_read_a_forfait_imposed_by_the_billing_without_code_instant_or_author() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.SIGNEE, T0, null, null, null,
                new ForfaitImpose(F1.id(), null, "Forfait 1", new BigDecimal("12.00"), null, null)));

        var impose = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow().forfaitImpose().orElseThrow();

        assertThat(impose.code()).isNull();
        assertThat(impose.imposeLe()).isNull();
        assertThat(impose.auteur()).isNull();
        assertThat(impose.prix()).isEqualByComparingTo("12.00");
    }

    @Test
    void should_have_no_forfait_imposed_on_a_session_that_never_received_one_and_refuse_to_record_none() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().trouver(CENTRE_A, s(1)).orElseThrow().forfaitImpose()).isEmpty();
        var sans = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        assertThatThrownBy(() -> banc.seances().enregistrerLeForfaitImpose(sans)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_refuse_to_record_the_forfait_of_a_session_that_does_not_exist() {
        var banc = nouveauBanc();
        var seance = programmee(1, CENTRE_A, PATIENT, JOUR, T0);
        seance.imposerUnForfait(F1, T1, COMPTA);

        assertThatThrownBy(() -> banc.seances().enregistrerLeForfaitImpose(seance)).isInstanceOf(IllegalStateException.class);
    }

    // ---- SE6 : jour, facture rattachée, suppression

    private static final FactureId FACTURE = new FactureId(new UUID(8, 1));

    @Test
    void should_read_the_invoice_that_took_the_session_and_none_for_a_session_never_billed() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.SIGNEE, T0, null, null, null, null, FACTURE));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0));

        assertThat(banc.seances().trouver(CENTRE_A, s(1)).orElseThrow().facture()).contains(FACTURE);
        assertThat(banc.seances().trouver(CENTRE_A, s(2)).orElseThrow().facture()).isEmpty();
    }

    @Test
    void should_write_only_the_day_and_keep_the_invoice_the_status_and_the_forfait_imposed() {
        var banc = nouveauBanc();
        banc.semer(Seance.restituer(s(1), CENTRE_A, PATIENT, JOUR, StatutDeSeance.SIGNEE, T0, null, new Seance.Jalons(T0, T0, T0, null),
                new Seance.Traces("inf-01", "med-01", null, null), new ForfaitImpose(F1.id(), "F1", "Forfait 1", new BigDecimal("250.50"),
                T1, "compta-01"), FACTURE));
        var seance = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        seance.changerDeJour(JOUR.plusDays(2));

        banc.seances().enregistrerLeJour(seance);

        var lue = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        assertThat(lue.jour()).isEqualTo(JOUR.plusDays(2));
        assertThat(lue.facture()).contains(FACTURE);
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(lue.traces().infirmier()).isEqualTo("inf-01");
        assertThat(lue.traces().medecin()).isEqualTo("med-01");
        assertThat(lue.forfaitImpose().orElseThrow().code()).isEqualTo("F1");
    }

    @Test
    void should_never_undo_a_concurrent_forfait_or_status_change_when_the_day_is_recorded() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        var lueAvant = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        var autre = banc.seances().trouverPourModifier(CENTRE_A, s(1)).orElseThrow();
        autre.imposerUnForfait(F1, T1, COMPTA);
        banc.seances().enregistrerLeForfaitImpose(autre);
        autre.validerParLInfirmier(T1, new Signataire("inf-01"));
        banc.seances().enregistrer(autre);

        lueAvant.changerDeJour(JOUR.minusDays(1));
        banc.seances().enregistrerLeJour(lueAvant);

        var finale = banc.seances().trouver(CENTRE_A, s(1)).orElseThrow();
        assertThat(finale.jour()).isEqualTo(JOUR.minusDays(1));
        assertThat(finale.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(finale.forfaitImpose().orElseThrow().code()).isEqualTo("F1");
    }

    @Test
    void should_refuse_to_record_the_day_of_a_session_that_does_not_exist() {
        var banc = nouveauBanc();

        assertThatThrownBy(() -> banc.seances().enregistrerLeJour(programmee(1, CENTRE_A, PATIENT, JOUR, T0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_remove_only_the_session_of_the_centre_and_leave_the_others() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(1, CENTRE_A, PATIENT, JOUR, T0));
        banc.seances().ajouter(programmee(2, CENTRE_A, PATIENT, JOUR, T0.plusSeconds(1)));
        banc.seances().ajouter(programmee(3, CENTRE_B, PATIENT, JOUR, T0));

        banc.seances().supprimer(banc.seances().trouver(CENTRE_A, s(1)).orElseThrow());

        assertThat(banc.seances().trouver(CENTRE_A, s(1))).isEmpty();
        assertThat(banc.seances().trouver(CENTRE_A, s(2))).isPresent();
        assertThat(banc.seances().trouver(CENTRE_B, s(3))).isPresent();
    }

    @Test
    void should_refuse_to_remove_a_session_that_does_not_exist_or_belongs_to_another_centre() {
        var banc = nouveauBanc();
        banc.seances().ajouter(programmee(3, CENTRE_B, PATIENT, JOUR, T0));
        var absente = programmee(1, CENTRE_A, PATIENT, JOUR, T0);
        var autreCentre = Seance.creer(s(3), CENTRE_A, PATIENT, JOUR, T0);

        assertThatThrownBy(() -> banc.seances().supprimer(absente)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> banc.seances().supprimer(autreCentre)).isInstanceOf(IllegalStateException.class);
        assertThat(banc.seances().trouver(CENTRE_B, s(3))).isPresent();
    }

    @Test
    void should_refuse_to_record_a_session_that_does_not_exist() {
        var banc = nouveauBanc();
        assertThatThrownBy(() -> banc.seances().enregistrer(programmee(1, CENTRE_A, PATIENT, JOUR, T0)))
                .isInstanceOf(IllegalStateException.class);
    }
}
