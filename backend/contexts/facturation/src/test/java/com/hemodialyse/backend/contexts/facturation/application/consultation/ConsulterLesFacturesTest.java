package com.hemodialyse.backend.contexts.facturation.application.consultation;

import com.hemodialyse.backend.contexts.facturation.application.consultation.SeancesDeLaFacture.SeanceRattachee;
import com.hemodialyse.backend.contexts.facturation.doubles.ListeDesFacturesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RG-FAC-046 à 048 : liste filtrée, triée, paginée et totalisée des factures émises, détail d'une facture. */
class ConsulterLesFacturesTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99995800-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("99995800-0000-0000-0000-0000000000b1"));
    private static final UUID CNAS = UUID.fromString("99995800-0000-0000-0000-000000000c01");
    private static final UUID CASNOS = UUID.fromString("99995800-0000-0000-0000-000000000c02");
    private static final UUID F1 = UUID.fromString("99995800-0000-0000-0000-0000000f0001");
    private static final UUID F2 = UUID.fromString("99995800-0000-0000-0000-0000000f0002");
    private static final UUID F3 = UUID.fromString("99995800-0000-0000-0000-0000000f0003");
    private static final UUID F4 = UUID.fromString("99995800-0000-0000-0000-0000000f0004");
    private static final UUID HD = UUID.fromString("99995800-0000-0000-0000-00000000f0d1");
    private static final UUID PREMIUM = UUID.fromString("99995800-0000-0000-0000-00000000f0d2");
    /** 2026-11-30T23:30Z = 2026-12-01 00:30 à Alger : le mois courant est décembre au fuseau du centre. */
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-30T23:30:00Z"), ZoneOffset.UTC);

    private final ListeDesFacturesEnMemoire memoire = new ListeDesFacturesEnMemoire();
    private final ConsulterLesFactures consulter = new ConsulterLesFactures(memoire, memoire, centre -> ZoneId.of("Africa/Algiers"),
            HORLOGE);

    @BeforeEach
    void factures() {
        memoire.poserLaFacture(CENTRE, F1, "FAC-2026-0001", LocalDate.of(2026, 11, 5), UUID.randomUUID(), "111", "BENALI", "Karim",
                CNAS, "CNAS", "9", "16000", "1440", "17440");
        memoire.poserLaFacture(CENTRE, F2, "FAC-2026-0002", LocalDate.of(2026, 11, 5), UUID.randomUUID(), "222", "amrani", "Lina",
                CASNOS, "CASNOS", "9", "8000", "720", "8720");
        memoire.poserLaFacture(CENTRE, F3, "FAC-2026-0003", LocalDate.of(2026, 11, 20), UUID.randomUUID(), "333", "Chikhi", "Nadia",
                null, "Non renseignee", "0", "4000", "0", "4000");
        memoire.poserLaFacture(CENTRE, F4, "FAC-2026-0004", LocalDate.of(2026, 12, 2), UUID.randomUUID(), "444", "Dahmani", "Ali",
                CNAS, "CNAS", "0", "500", "0", "500");
        memoire.poserLaFacture(AUTRE_CENTRE, UUID.randomUUID(), "FAC-B-0001", LocalDate.of(2026, 11, 6), "999");
        memoire.poserLaLigne(CENTRE, F1, UUID.randomUUID(), new LigneFacture(HD, "Hemodialyse standard", new BigDecimal("8000"), 2));
        memoire.poserLeReglement(CENTRE, F1, "17440");
        memoire.poserLeReglement(CENTRE, F2, "5000");
        memoire.poserLeReglement(CENTRE, F2, "-1000");
    }

    private static CriteresDeFactures novembre() {
        return CriteresDeFactures.duCentre(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    }

    private static CriteresDeFactures avec(TriDesFactures tri) {
        return new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, null, null, null, null, null,
                tri);
    }

    @Test
    void should_list_the_invoices_of_the_period_by_date_then_number_descending_with_the_totals_of_all_of_them() {
        PageDeFactures page = consulter.rechercher(novembre(), 0, 20);

        assertThat(page.items()).extracting(FactureListee::factureId).containsExactly(F3, F2, F1);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.du()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(page.au()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(page.totaux()).isEqualTo(new TotauxDesFactures(3, new BigDecimal("28000.00"), new BigDecimal("2160.00"),
                new BigDecimal("30160.00"), new BigDecimal("21440.00"), new BigDecimal("8720.00")));
        FactureListee f2 = page.items().get(1);
        assertThat(f2.montantRegle()).isEqualByComparingTo("4000");
        assertThat(f2.reste()).isEqualByComparingTo("4720");
        assertThat(f2.etat()).isEqualTo(EtatDeReglement.PARTIELLEMENT_REGLEE);
        assertThat(page.items().get(2).nombreDeSeances()).isEqualTo(2);
    }

    @Test
    void should_default_the_period_to_the_current_month_in_the_time_zone_of_the_centre() {
        PageDeFactures page = consulter.rechercher(CriteresDeFactures.duCentre(CENTRE, null, null), 0, 20);

        assertThat(page.du()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(page.au()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(page.items()).extracting(FactureListee::factureId).containsExactly(F4);
    }

    @Test
    void should_refuse_a_period_with_a_single_bound_reversed_or_longer_than_366_days() {
        assertThatThrownBy(() -> consulter.rechercher(CriteresDeFactures.duCentre(CENTRE, LocalDate.of(2026, 11, 1), null), 0, 20))
                .isInstanceOf(CriteresDeFacturesInvalides.class).extracting("code").isEqualTo("PERIODE_INVALIDE");
        assertThatThrownBy(() -> consulter.rechercher(CriteresDeFactures.duCentre(CENTRE, null, LocalDate.of(2026, 11, 1)), 0, 20))
                .extracting("code").isEqualTo("PERIODE_INVALIDE");
        assertThatThrownBy(() -> consulter.rechercher(
                CriteresDeFactures.duCentre(CENTRE, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)), 0, 20))
                .extracting("code").isEqualTo("PERIODE_INVALIDE");
        assertThatThrownBy(() -> consulter.rechercher(
                CriteresDeFactures.duCentre(CENTRE, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)), 0, 20))
                .extracting("code").isEqualTo("PERIODE_INVALIDE");
        // 366 jours bornes incluses : accepté
        assertThat(consulter.rechercher(CriteresDeFactures.duCentre(CENTRE, LocalDate.of(2025, 12, 31), LocalDate.of(2026, 12, 31)), 0, 20)
                .total()).isEqualTo(4);
    }

    @Test
    void should_filter_on_number_patient_insurance_number_caisse_state_and_amount_cumulatively() {
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "fac-2026-000", null, null,
                null, null, null, null))).containsExactly(F3, F2, F1);
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "  0002 ", null, null,
                null, null, null, null))).containsExactly(F2);
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, "AMRANI li", null,
                null, null, null, null))).containsExactly(F2);
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, "333", null,
                null, null, null, null))).containsExactly(F3);
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, null, CNAS,
                null, null, null, null))).containsExactly(F1);
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, null, null,
                EtatDeReglement.NON_REGLEE, null, null, null))).containsExactly(F3);
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, null, null,
                null, new BigDecimal("4000"), new BigDecimal("8720"), null))).containsExactly(F3, F2);
        assertThat(ids(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "FAC", "a", CASNOS,
                EtatDeReglement.PARTIELLEMENT_REGLEE, new BigDecimal("8720"), null, null))).containsExactly(F2);
        PageDeFactures filtree = consulter.rechercher(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), null, null, CNAS, null, null, null, null), 0, 20);
        assertThat(filtree.totaux().nombreDeFactures()).isEqualTo(1);
        assertThat(filtree.totaux().montantTtc()).isEqualByComparingTo("17440");
    }

    @Test
    void should_refuse_a_minimum_amount_above_the_maximum() {
        assertThatThrownBy(() -> consulter.rechercher(new CriteresDeFactures(CENTRE, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), null, null, null, null, new BigDecimal("10"), new BigDecimal("9.99"), null), 0, 20))
                .isInstanceOf(CriteresDeFacturesInvalides.class).extracting("code").isEqualTo("MONTANT_INVALIDE");
    }

    @Test
    void should_sort_on_the_requested_column_and_break_ties_by_date_then_number_descending() {
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.PATIENT, false)))).containsExactly(F2, F1, F3);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.MONTANT_TTC, true)))).containsExactly(F1, F2, F3);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.ETAT, false)))).containsExactly(F3, F2, F1);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.RESTE, false)))).containsExactly(F1, F3, F2);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.DATE_FACTURATION, false)))).containsExactly(F2, F1, F3);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.CAISSE, false)))).containsExactly(F2, F1, F3);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.NOMBRE_DE_SEANCES, true)))).containsExactly(F1, F3, F2);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.NUMERO_FACTURE, false)))).containsExactly(F1, F2, F3);
        assertThat(ids(avec(new TriDesFactures(ChampDeTri.MONTANT_REGLE, false)))).containsExactly(F3, F2, F1);
    }

    @Test
    void should_bound_the_page_and_its_size_without_error() {
        PageDeFactures page = consulter.rechercher(novembre(), 1, 2);
        assertThat(page.items()).extracting(FactureListee::factureId).containsExactly(F1);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.totaux().nombreDeFactures()).isEqualTo(3);

        PageDeFactures negative = consulter.rechercher(novembre(), -3, 0);
        assertThat(negative.page()).isZero();
        assertThat(negative.taille()).isEqualTo(20);
        assertThat(consulter.rechercher(novembre(), 0, 1000).taille()).isEqualTo(100);
        assertThat(consulter.rechercher(novembre(), 9, 20).items()).isEmpty();
    }

    @Test
    void should_detail_an_invoice_with_its_frozen_lines_its_seances_and_its_frozen_totals() {
        UUID f5 = UUID.fromString("99995800-0000-0000-0000-0000000f0005");
        memoire.poserLaFacture(CENTRE, f5, "FAC-2026-0005", LocalDate.of(2026, 11, 25), UUID.randomUUID(), "555", "Erraji", "Sami",
                CNAS, "CNAS", "9", "25000.05", "2250.00", "27250.05");
        memoire.poserLaLigne(CENTRE, f5, UUID.randomUUID(), new LigneFacture(HD, "Hemodialyse standard", new BigDecimal("7777.77"), 2));
        memoire.poserLaLigne(CENTRE, f5, UUID.randomUUID(), new LigneFacture(PREMIUM, "HD Premium", new BigDecimal("9444.51"), 1));
        memoire.poserLeReglement(CENTRE, f5, "1000");
        UUID s1 = UUID.fromString("99995800-0000-0000-0000-00000000a001");
        UUID s2 = UUID.fromString("99995800-0000-0000-0000-00000000a002");
        UUID s3 = UUID.fromString("99995800-0000-0000-0000-00000000a003");
        memoire.rattacher(CENTRE, f5, new SeanceRattachee(s3, LocalDate.of(2026, 10, 9), "FACTUREE", PREMIUM));
        memoire.rattacher(CENTRE, f5, new SeanceRattachee(s1, LocalDate.of(2026, 10, 2), "FACTUREE", null));
        memoire.rattacher(CENTRE, f5, new SeanceRattachee(s2, LocalDate.of(2026, 10, 5), "FACTUREE", null));

        DetailDeFacture detail = consulter.detail(CENTRE, f5).orElseThrow();

        assertThat(detail.numeroFacture()).isEqualTo("FAC-2026-0005");
        assertThat(detail.tauxTva()).isEqualByComparingTo("9");
        assertThat(detail.lignes()).containsExactly(
                new LigneDeFactureDetaillee(PREMIUM, "HD Premium", new BigDecimal("9444.51"), 1, new BigDecimal("9444.51"),
                        new BigDecimal("850.01"), new BigDecimal("10294.52")),
                new LigneDeFactureDetaillee(HD, "Hemodialyse standard", new BigDecimal("7777.77"), 2, new BigDecimal("15555.54"),
                        new BigDecimal("1400.00"), new BigDecimal("16955.54")));
        assertThat(detail.seances()).containsExactly(
                new SeanceDeFactureDetaillee(s1, LocalDate.of(2026, 10, 2), "FACTUREE", HD, "Hemodialyse standard", new BigDecimal("7777.77")),
                new SeanceDeFactureDetaillee(s2, LocalDate.of(2026, 10, 5), "FACTUREE", HD, "Hemodialyse standard", new BigDecimal("7777.77")),
                new SeanceDeFactureDetaillee(s3, LocalDate.of(2026, 10, 9), "FACTUREE", PREMIUM, "HD Premium", new BigDecimal("9444.51")));
        assertThat(detail.totaux()).isEqualTo(new DetailDeFacture.Totaux(new BigDecimal("25000.05"), new BigDecimal("2250.00"),
                new BigDecimal("27250.05"), new BigDecimal("1000.00"), new BigDecimal("26250.05")));
    }

    @Test
    void should_show_a_seance_whose_forfait_cannot_be_attributed_without_forfait() {
        memoire.poserLaLigne(CENTRE, F2, UUID.randomUUID(), new LigneFacture(HD, "Hemodialyse standard", new BigDecimal("4000"), 1));
        memoire.poserLaLigne(CENTRE, F2, UUID.randomUUID(), new LigneFacture(PREMIUM, "HD Premium", new BigDecimal("4000"), 1));
        UUID s = UUID.randomUUID();
        memoire.rattacher(CENTRE, F2, new SeanceRattachee(s, LocalDate.of(2026, 10, 2), "FACTUREE", null));

        assertThat(consulter.detail(CENTRE, F2).orElseThrow().seances())
                .containsExactly(new SeanceDeFactureDetaillee(s, LocalDate.of(2026, 10, 2), "FACTUREE", null, null, null));
    }

    @Test
    void should_not_detail_an_unknown_invoice_nor_one_of_another_centre() {
        assertThat(consulter.detail(CENTRE, UUID.randomUUID())).isEmpty();
        assertThat(consulter.detail(AUTRE_CENTRE, F1)).isEmpty();
    }

    private java.util.List<UUID> ids(CriteresDeFactures criteres) {
        return consulter.rechercher(criteres, 0, 20).items().stream().map(FactureListee::factureId).toList();
    }
}
