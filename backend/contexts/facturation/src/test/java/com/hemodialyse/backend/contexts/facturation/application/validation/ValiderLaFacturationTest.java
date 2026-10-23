package com.hemodialyse.backend.contexts.facturation.application.validation;

import com.hemodialyse.backend.contexts.facturation.application.simulation.DemandeDeSimulation;
import com.hemodialyse.backend.contexts.facturation.application.simulation.TauxDeTvaApplicable;
import com.hemodialyse.backend.contexts.facturation.application.cloture.GardeDesMoisClos;
import com.hemodialyse.backend.contexts.facturation.doubles.FacturesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.MoisDeFacturationEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.ParametresEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.PublicationEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.SeancesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.SequenceEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Facture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.NumeroDeFactureDejaPris;
import com.hemodialyse.backend.contexts.facturation.domain.facture.SeancesSansForfait;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.NumerotationPrevisionnelle;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PatientFacture;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Regroupement;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage de la validation de facturation avec des doubles en mémoire. Horloge fixe : 20 octobre 2026 (jour de validation) ;
 * les séances sont d'août 2026 et le taux de TVA varie selon la date demandée, pour prouver que c'est celui de la fin de période.
 */
class ValiderLaFacturationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996900-0000-0000-0000-00000000000a"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("99996900-0000-0000-0000-00000000000b"));
    private static final UUID AMRANI = UUID.fromString("99996900-0000-0000-0000-00000000a001");
    private static final UUID BELKACEM = UUID.fromString("99996900-0000-0000-0000-00000000a002");
    private static final UUID FORFAIT_HD = UUID.fromString("99996900-0000-0000-0000-0000000f0001");
    private static final UUID FORFAIT_PREMIUM = UUID.fromString("99996900-0000-0000-0000-0000000f0002");
    private static final UUID PAYEUR = UUID.fromString("99996900-0000-0000-0000-000000000c01");
    private static final UUID AGENCE = UUID.fromString("99996900-0000-0000-0000-000000000a61");
    private static final UUID AUTEUR = UUID.fromString("99996900-0000-0000-0000-00000000c001");
    private static final Instant MAINTENANT = Instant.parse("2026-10-20T08:00:00Z");
    private static final DemandeDeSimulation AOUT = new DemandeDeSimulation(YearMonth.of(2026, 8), null, null, true);

    private final SeancesEnMemoire seances = new SeancesEnMemoire();
    private final ParametresEnMemoire parametres = new ParametresEnMemoire();
    private final SequenceEnMemoire sequence = new SequenceEnMemoire();
    private final FacturesEnMemoire factures = new FacturesEnMemoire();
    private final PublicationEnMemoire publication = new PublicationEnMemoire();
    private final MoisDeFacturationEnMemoire moisDeFacturation = new MoisDeFacturationEnMemoire();
    private final GardeDesMoisClos garde = new GardeDesMoisClos(moisDeFacturation);
    private final List<LocalDate> datesDeTva = new ArrayList<>();
    private final ValiderLaFacturation valider;

    ValiderLaFacturationTest() {
        TauxDeTvaApplicable tva = (centre, date) -> {
            datesDeTva.add(date);
            return new TauxDeTva(date.isBefore(LocalDate.of(2026, 9, 1)) ? new BigDecimal("9") : new BigDecimal("19"));
        };
        valider = new ValiderLaFacturation(seances, parametres, sequence, tva, factures, seances, publication,
                new UniteDeTravailDirecte(), Clock.fixed(MAINTENANT, ZoneOffset.UTC), new Identifiants()::suivant, garde);
        seances.forfait(FORFAIT_HD, CENTRE, "Forfait HD", new BigDecimal("3500.00"));
        seances.forfait(FORFAIT_PREMIUM, CENTRE, "Forfait HD Premium", new BigDecimal("5200.00"));
        seances.patient(AMRANI, CENTRE, "P-001", "Amrani", "Lina", "PERMANENT", "ASS-001", PAYEUR, AGENCE);
        seances.patient(BELKACEM, CENTRE, "P-002", "Belkacem", "Omar", "PERMANENT");
        ForfaitApplique hd = new ForfaitApplique(FORFAIT_HD, "Forfait HD", new BigDecimal("3500.00"));
        for (UUID patient : List.of(AMRANI, BELKACEM)) {
            seances.priseEnCharge(patient, new PriseEnChargeTarifee(hd, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    null, null, Instant.parse("2026-01-01T08:00:00Z")));
        }
        sequence.poser(CENTRE, 2026, 41);
    }

    private UUID seance(UUID patient, int jourDAout, String statut) {
        UUID id = UUID.randomUUID();
        seances.seance(id, CENTRE, patient, LocalDate.of(2026, 8, jourDAout), statut, false);
        return id;
    }

    @Test
    void should_emit_one_invoice_per_patient_and_link_the_seances_to_it() {
        UUID s1 = seance(AMRANI, 2, "VALIDEE");
        UUID s2 = seance(AMRANI, 4, "SIGNEE");
        UUID s3 = seance(BELKACEM, 3, "VALIDEE");

        ResultatDeLaValidation resultat = valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(resultat).isEqualTo(new ResultatDeLaValidation(2, 3));
        assertThat(factures.emises()).extracting(Facture::numero).containsExactly("FAC-2026-0042", "FAC-2026-0043");
        Facture amrani = factures.emises().getFirst();
        assertThat(amrani.patient().id()).isEqualTo(AMRANI);
        assertThat(amrani.lignes()).containsExactly(new LigneFacture(FORFAIT_HD, "Forfait HD", new BigDecimal("3500.00"), 2));
        assertThat(amrani.totalHt()).isEqualByComparingTo("7000.00");
        assertThat(amrani.emisePar()).isEqualTo(AUTEUR);
        assertThat(seances.factureDe(s1)).isEqualTo(amrani.id());
        assertThat(seances.factureDe(s2)).isEqualTo(amrani.id());
        assertThat(seances.factureDe(s3)).isEqualTo(factures.emises().get(1).id());
        assertThat(seances.statutDe(s1)).isEqualTo("FACTUREE");
    }

    @Test
    void should_freeze_the_payer_and_the_registration_number_of_the_patient() {
        seance(AMRANI, 2, "VALIDEE");

        valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(factures.emises().getFirst().patient().numeroDImmatriculation()).isEqualTo("ASS-001");
        assertThat(factures.emises().getFirst().patient().centrePayeur()).isEqualTo(PAYEUR);
        assertThat(factures.emises().getFirst().patient().agence()).isEqualTo(AGENCE);
    }

    @Test
    void should_reserve_the_sequence_of_the_year_of_the_day_and_date_the_invoices_with_the_day() {
        seance(AMRANI, 2, "VALIDEE");
        seance(BELKACEM, 3, "VALIDEE");

        valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(sequence.courante(CENTRE, 2026)).isEqualTo(43);
        assertThat(factures.emises()).allSatisfy(f -> assertThat(f.dateDeFacturation()).isEqualTo(LocalDate.of(2026, 10, 20)));
        assertThat(factures.emises()).allSatisfy(f -> assertThat(f.periode().fin()).isEqualTo(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void should_number_with_the_centre_template() {
        parametres.enregistrer(CENTRE, ParametresDeFacturation.saisis("{CENTER}/F{SEQ6}", true, MAINTENANT), "admin");
        seance(AMRANI, 2, "VALIDEE");

        valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(factures.emises().getFirst().numero()).isEqualTo("99996900/F000042");
    }

    @Test
    void should_group_by_the_request_not_by_the_centre_settings() {
        parametres.enregistrer(CENTRE, ParametresDeFacturation.saisis("FAC-{SEQ}", true, MAINTENANT), "admin");
        seance(AMRANI, 2, "VALIDEE");
        UUID surcharge = seance(AMRANI, 3, "VALIDEE");
        seances.surcharge(surcharge, new ForfaitApplique(FORFAIT_PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")));

        ResultatDeLaValidation separees = valider.valider(CENTRE, new DemandeDeSimulation(YearMonth.of(2026, 8), null, null, false),
                AUTEUR);

        assertThat(separees.facturesEmises()).isEqualTo(2);
        assertThat(factures.emises()).extracting(f -> f.lignes().size()).containsExactly(1, 1);
    }

    @Test
    void should_apply_the_vat_in_force_at_the_end_of_the_period_not_the_day_of_the_validation() {
        seance(AMRANI, 2, "VALIDEE");

        valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(datesDeTva).containsOnly(LocalDate.of(2026, 8, 31));
        assertThat(factures.emises().getFirst().taux().pourcentage()).isEqualByComparingTo("9");
        assertThat(factures.emises().getFirst().totalTva()).isEqualByComparingTo("315.00");
    }

    @Test
    void should_choose_the_period_of_the_dates_when_there_is_no_month() {
        seance(AMRANI, 2, "VALIDEE");
        seance(AMRANI, 20, "VALIDEE");

        valider.valider(CENTRE, new DemandeDeSimulation(null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), true), AUTEUR);

        assertThat(factures.emises().getFirst().seances()).hasSize(1);
        assertThat(factures.emises().getFirst().periode().fin()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void should_prefer_the_month_over_the_dates() {
        seance(AMRANI, 2, "VALIDEE");
        seance(AMRANI, 20, "VALIDEE");

        valider.valider(CENTRE, new DemandeDeSimulation(YearMonth.of(2026, 8), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10),
                true), AUTEUR);

        assertThat(factures.emises().getFirst().seances()).hasSize(2);
        assertThat(publication.publies().getFirst().fin()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void should_publish_an_event_that_describes_exactly_what_was_emitted() {
        seance(AMRANI, 2, "VALIDEE");
        seance(AMRANI, 4, "VALIDEE");
        seance(BELKACEM, 3, "VALIDEE");

        valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(publication.publies()).hasSize(1);
        FacturationValideeEvent evenement = publication.publies().getFirst();
        assertThat(evenement.centre()).isEqualTo(CENTRE.valeur());
        assertThat(evenement.debut()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(evenement.fin()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(evenement.seancesFacturees()).isEqualTo(3);
        assertThat(evenement.survenueLe()).isEqualTo(MAINTENANT);
        // Événement v3 : le détail des factures n'est plus dans l'événement ; mêmes assertions sur les factures émises.
        assertThat(evenement.nombreDeFactures()).isEqualTo(factures.emises().size()).isEqualTo(2);
        assertThat(factures.emises()).extracting(Facture::numero).containsExactly("FAC-2026-0042", "FAC-2026-0043");
        assertThat(factures.emises().getFirst().totalHt()).isEqualByComparingTo("7000.00");
        assertThat(factures.emises().getFirst().totalTva()).isEqualByComparingTo("630.00");
        assertThat(factures.emises().getFirst().totalTtc()).isEqualByComparingTo("7630.00");
        assertThat(evenement.totalTtc()).isEqualByComparingTo("11445.00");
    }

    @Test
    void should_emit_nothing_publish_nothing_and_keep_the_sequence_when_no_seance_is_eligible() {
        seance(AMRANI, 2, "BROUILLON");
        seance(AMRANI, 3, "ABSENT");
        seances.seance(UUID.randomUUID(), CENTRE, AMRANI, LocalDate.of(2026, 9, 1), "VALIDEE", false);
        seances.seance(UUID.randomUUID(), CENTRE, AMRANI, LocalDate.of(2026, 8, 5), "VALIDEE", true);

        ResultatDeLaValidation resultat = valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(resultat).isEqualTo(new ResultatDeLaValidation(0, 0));
        assertThat(factures.emises()).isEmpty();
        assertThat(publication.publies()).isEmpty();
        assertThat(sequence.courante(CENTRE, 2026)).isEqualTo(41);
    }

    @Test
    void should_not_emit_for_another_centre() {
        seances.patient(UUID.randomUUID(), AUTRE_CENTRE, "P-9", "Zerrouki", "Rania", null);
        seance(AMRANI, 2, "VALIDEE");

        valider.valider(AUTRE_CENTRE, AOUT, AUTEUR);

        assertThat(factures.emises()).isEmpty();
    }

    @Test
    void should_not_bill_a_seance_twice() {
        seance(AMRANI, 2, "VALIDEE");

        valider.valider(CENTRE, AOUT, AUTEUR);
        ResultatDeLaValidation seconde = valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(seconde).isEqualTo(new ResultatDeLaValidation(0, 0));
        assertThat(factures.emises()).hasSize(1);
        assertThat(publication.publies()).hasSize(1);
    }

    @Test
    void should_refuse_the_validation_and_emit_nothing_while_a_seance_has_no_forfait() {
        seance(AMRANI, 2, "VALIDEE");
        UUID sansPrise = UUID.fromString("99996900-0000-0000-0000-00000000a003");
        seances.patient(sansPrise, CENTRE, "P-003", "Chikhi", "Nadia", "PERMANENT");
        seances.seance(UUID.randomUUID(), CENTRE, sansPrise, LocalDate.of(2026, 8, 5), "VALIDEE", false);

        assertThatThrownBy(() -> valider.valider(CENTRE, AOUT, AUTEUR)).isInstanceOf(SeancesSansForfait.class)
                .hasMessageContaining("1 seance eligible n'a pas de forfait defini");

        assertThat(factures.emises()).isEmpty();
        assertThat(publication.publies()).isEmpty();
        assertThat(sequence.courante(CENTRE, 2026)).isEqualTo(41);
    }

    @Test
    void should_accept_a_seance_whose_forfait_comes_from_a_surcharge() {
        UUID sansPrise = UUID.fromString("99996900-0000-0000-0000-00000000a003");
        seances.patient(sansPrise, CENTRE, "P-003", "Chikhi", "Nadia", "PERMANENT");
        UUID id = UUID.randomUUID();
        seances.seance(id, CENTRE, sansPrise, LocalDate.of(2026, 8, 5), "VALIDEE", false);
        seances.surcharge(id, new ForfaitApplique(FORFAIT_PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")));

        assertThat(valider.valider(CENTRE, AOUT, AUTEUR).facturesEmises()).isEqualTo(1);
    }

    @Test
    void should_refuse_a_number_that_is_already_taken_and_publish_nothing() {
        seance(AMRANI, 2, "VALIDEE");
        factures.dejaEmise(deja("FAC-2026-0042"));

        assertThatThrownBy(() -> valider.valider(CENTRE, AOUT, AUTEUR)).isInstanceOf(NumeroDeFactureDejaPris.class)
                .hasMessageContaining("FAC-2026-0042")
                .satisfies(e -> assertThat(((NumeroDeFactureDejaPris) e).numero()).isEqualTo("FAC-2026-0042"));

        assertThat(factures.emises()).hasSize(1);
        assertThat(publication.publies()).isEmpty();
    }

    @Test
    void should_take_the_next_number_on_a_new_attempt_after_a_refused_one() {
        seance(AMRANI, 2, "VALIDEE");
        factures.dejaEmise(deja("FAC-2026-0042"));
        assertThatThrownBy(() -> valider.valider(CENTRE, AOUT, AUTEUR)).isInstanceOf(NumeroDeFactureDejaPris.class);

        valider.valider(CENTRE, AOUT, AUTEUR);

        assertThat(factures.emises()).extracting(Facture::numero).containsExactly("FAC-2026-0042", "FAC-2026-0043");
    }

    @Test
    void should_refuse_the_validation_of_a_closed_month_without_emitting_nor_moving_the_sequence() {
        seance(AMRANI, 2, "VALIDEE");
        moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 8));

        assertThatThrownBy(() -> valider.valider(CENTRE, AOUT, AUTEUR))
                .isInstanceOf(com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationClos.class)
                .hasMessageContaining("2026-08");

        assertThat(factures.emises()).isEmpty();
        assertThat(sequence.courante(CENTRE, 2026)).isEqualTo(41);
        assertThat(publication.publies()).isEmpty();
    }

    @Test
    void should_refuse_a_period_when_any_month_it_covers_is_closed_and_lock_the_months_in_order() {
        seance(AMRANI, 2, "VALIDEE");
        moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 9));
        DemandeDeSimulation aoutAOctobre = new DemandeDeSimulation(null, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 10, 15), true);

        assertThatThrownBy(() -> valider.valider(CENTRE, aoutAOctobre, AUTEUR))
                .isInstanceOf(com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationClos.class)
                .hasMessageContaining("2026-09");

        assertThat(factures.emises()).isEmpty();
        // F7 bis (revue, constat 3) : tous les mois de l'intervalle sont verrouillés ensemble, dans l'ordre croissant, puis le premier
        // mois clos est refusé (la transaction est annulée)
        assertThat(moisDeFacturation.moisVerrouilles()).containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
    }

    @Test
    void should_refuse_a_closed_month_even_when_it_has_nothing_to_emit() {
        moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 8));

        assertThatThrownBy(() -> valider.valider(CENTRE, AOUT, AUTEUR))
                .isInstanceOf(com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationClos.class);
    }

    @Test
    void should_validate_normally_when_only_another_month_or_another_centre_is_closed() {
        seance(AMRANI, 2, "VALIDEE");
        moisDeFacturation.poserClos(CENTRE, YearMonth.of(2026, 7));
        moisDeFacturation.poserClos(new CentreId(UUID.randomUUID()), YearMonth.of(2026, 8));

        assertThat(valider.valider(CENTRE, AOUT, AUTEUR).facturesEmises()).isEqualTo(1);
    }

    @Test
    void should_fail_the_whole_validation_when_a_seance_is_no_longer_eligible_at_the_link() {
        seance(AMRANI, 2, "VALIDEE");
        LiaisonQuiEchoue echec = new LiaisonQuiEchoue();
        ValiderLaFacturation fragile = new ValiderLaFacturation(seances, parametres, sequence,
                (centre, date) -> TauxDeTva.AUCUN, factures, echec, publication, new UniteDeTravailDirecte(),
                Clock.fixed(MAINTENANT, ZoneOffset.UTC), UUID::randomUUID, garde);

        assertThatThrownBy(() -> fragile.valider(CENTRE, AOUT, AUTEUR)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Une ou plusieurs seances ne sont plus eligibles a la validation");

        assertThat(publication.publies()).isEmpty();
    }

    @Test
    void should_refuse_a_period_that_ends_before_it_starts() {
        assertThatThrownBy(() -> valider.valider(CENTRE,
                new DemandeDeSimulation(null, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1), true), AUTEUR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuse_a_missing_period() {
        assertThatThrownBy(() -> valider.valider(CENTRE, new DemandeDeSimulation(null, null, null, true), AUTEUR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_emit_without_author_when_the_session_gives_none() {
        seance(AMRANI, 2, "VALIDEE");

        valider.valider(CENTRE, AOUT, null);

        assertThat(factures.emises().getFirst().emisePar()).isNull();
    }

    private Facture deja(String numero) {
        PeriodeDeFacturation juillet = new PeriodeDeFacturation(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        SeanceFacturable seance = new SeanceFacturable(UUID.randomUUID(), new PatientFacture(AMRANI, "P", "A", "B", null),
                LocalDate.of(2026, 7, 2), StatutDeSeance.VALIDEE, new ForfaitApplique(FORFAIT_HD, "Forfait HD", new BigDecimal("100.00")));
        FactureSimulee groupe = Simulation.etablir(CENTRE, MAINTENANT, juillet, Regroupement.PAR_PATIENT, List.of(seance),
                        TauxDeTva.AUCUN, new NumerotationPrevisionnelle(ModeleDeNumerotation.PAR_DEFAUT, LocalDate.of(2026, 7, 31), 0))
                .factures().getFirst();
        return Facture.emettre(UUID.randomUUID(), CENTRE, groupe, numero, juillet, LocalDate.of(2026, 7, 31), TauxDeTva.AUCUN, null);
    }

    private static final class Identifiants {
        private int n;

        UUID suivant() {
            return new UUID(0, ++n);
        }
    }

    private static final class LiaisonQuiEchoue implements LiaisonDesSeances {
        @Override
        public void lier(CentreId centre, UUID facture, List<UUID> seances) {
            throw new IllegalStateException("Une ou plusieurs seances ne sont plus eligibles a la validation");
        }
    }
}
