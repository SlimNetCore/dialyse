package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.doubles.PublicationDesReglementsEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.ReglementsEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsulterLesReglementsTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99995800-0000-0000-0000-0000000000a1"));
    private static final UUID F1 = UUID.fromString("99995800-0000-0000-0000-0000000f0001");
    private static final UUID F2 = UUID.fromString("99995800-0000-0000-0000-0000000f0002");
    private static final UUID F3 = UUID.fromString("99995800-0000-0000-0000-0000000f0003");

    private final ReglementsEnMemoire memoire = new ReglementsEnMemoire();
    private ConsulterLesReglements consulter;
    private EnregistrerUnReglement enregistrer;

    @BeforeEach
    void preparer() {
        memoire.poserLaFacture(CENTRE, F1, "FAC-0001", LocalDate.of(2026, 1, 31), new BigDecimal("1000.00"));
        memoire.poserLaFacture(CENTRE, F2, "FAC-0002", LocalDate.of(2026, 1, 31), new BigDecimal("500.00"));
        memoire.poserLaFacture(CENTRE, F3, "FAC-0003", LocalDate.of(2026, 2, 28), new BigDecimal("200.00"));
        consulter = new ConsulterLesReglements(memoire, memoire);
        enregistrer = new EnregistrerUnReglement(memoire, memoire, new PublicationDesReglementsEnMemoire(),
                new UniteDeTravailDirecte(), Clock.systemDefaultZone(), UUID::randomUUID);
    }

    private void regler(UUID facture, String montant, String jour) {
        enregistrer.enregistrer(CENTRE, facture, new DemandeDeReglement(new BigDecimal(montant), LocalDate.parse(jour), null, null, null), null);
    }

    @Test
    void should_search_the_invoices_of_the_year_newest_first() {
        PageDeSituations page = consulter.rechercher(new CriteresDeSituation(CENTRE, 2026, null, null, null, null), 0, 20);

        assertThat(page.items()).extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0003", "FAC-0002", "FAC-0001");
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void should_filter_by_month_including_the_boundaries() {
        assertThat(consulter.rechercher(new CriteresDeSituation(CENTRE, 2026, 1, null, null, null), 0, 20).items())
                .extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0002", "FAC-0001");
        assertThat(consulter.rechercher(new CriteresDeSituation(CENTRE, 2026, 2, null, null, null), 0, 20).items())
                .extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0003");
    }

    @Test
    void should_paginate() {
        PageDeSituations deuxieme = consulter.rechercher(new CriteresDeSituation(CENTRE, 2026, null, null, null, null), 1, 2);

        assertThat(deuxieme.items()).extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0001");
        assertThat(deuxieme.total()).isEqualTo(3);
    }

    @Test
    void should_refuse_a_negative_page_and_an_empty_page_size() {
        CriteresDeSituation criteres = new CriteresDeSituation(CENTRE, 2026, null, null, null, null);

        assertThatThrownBy(() -> consulter.rechercher(criteres, -1, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La page doit etre positive");
        assertThatThrownBy(() -> consulter.rechercher(criteres, 0, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La taille de page doit etre strictement positive");
    }

    @Test
    void should_validate_the_criteria() {
        assertThatThrownBy(() -> new CriteresDeSituation(null, 2026, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le centerId est obligatoire");
        assertThatThrownBy(() -> new CriteresDeSituation(CENTRE, 1999, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("L'annee doit etre comprise entre 2000 et 3000");
        assertThatThrownBy(() -> new CriteresDeSituation(CENTRE, 3001, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CriteresDeSituation(CENTRE, 2026, 0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le mois doit etre compris entre 1 et 12");
        assertThatThrownBy(() -> new CriteresDeSituation(CENTRE, 2026, 13, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CriteresDeSituation(CENTRE, 2000, 1, null, null, null).debut()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(new CriteresDeSituation(CENTRE, 3000, 12, null, null, null).fin()).isEqualTo(LocalDate.of(3000, 12, 31));
        assertThat(new CriteresDeSituation(CENTRE, 2028, 2, null, null, null).fin()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(new CriteresDeSituation(CENTRE, 2026, null, null, null, null).fin()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void should_establish_the_dashboard_as_before() {
        regler(F1, "1000.00", "2026-02-01");
        regler(F2, "600.00", "2026-02-02");
        regler(F3, "50.00", "2026-03-01");

        TableauDeBord tableau = consulter.tableauDeBord(new CriteresDeSituation(CENTRE, 2026, null, null, null, null));

        assertThat(tableau.totalFactures()).isEqualTo(3);
        assertThat(tableau.nonReglees()).isZero();
        assertThat(tableau.partiellementReglees()).isEqualTo(1);
        assertThat(tableau.reglees()).isEqualTo(2);
        assertThat(tableau.tropPercus()).isEqualTo(1);
        assertThat(tableau.totalFacture()).isEqualByComparingTo("1700.00");
        assertThat(tableau.totalRegle()).isEqualByComparingTo("1650.00");
        assertThat(tableau.totalReste()).isEqualByComparingTo("150.00");
        assertThat(tableau.totalTropPercu()).isEqualByComparingTo("100.00");
        assertThat(tableau.statusBreakdown()).extracting(TableauDeBord.Tranche::code, TableauDeBord.Tranche::label,
                TableauDeBord.Tranche::count).containsExactly(
                org.assertj.core.groups.Tuple.tuple("NON_REGLEE", "Non reglee", 0L),
                org.assertj.core.groups.Tuple.tuple("PARTIELLEMENT_REGLEE", "Partiellement reglee", 1L),
                org.assertj.core.groups.Tuple.tuple("REGLE", "Reglee", 1L),
                org.assertj.core.groups.Tuple.tuple("TROP_PERCU", "Trop percu", 1L));
        assertThat(tableau.statusBreakdown()).extracting(TableauDeBord.Tranche::amount).usingComparatorForType(
                BigDecimal::compareTo, BigDecimal.class).containsExactly(new BigDecimal("0.00"), new BigDecimal("200.00"),
                new BigDecimal("1000.00"), new BigDecimal("100.00"));
    }

    @Test
    void should_give_an_empty_dashboard_when_there_is_no_invoice() {
        TableauDeBord tableau = consulter.tableauDeBord(new CriteresDeSituation(CENTRE, 2030, null, null, null, null));

        assertThat(tableau.totalFactures()).isZero();
        assertThat(tableau.totalFacture()).isEqualByComparingTo("0.00");
        assertThat(tableau.statusBreakdown()).hasSize(4);
        assertThat(tableau.centerId()).isEqualTo(CENTRE.valeur());
        assertThat(tableau.year()).isEqualTo(2030);
        assertThat(tableau.month()).isNull();
    }

    @Test
    void should_export_all_the_lines_of_the_filter() {
        assertThat(consulter.exporter(new CriteresDeSituation(CENTRE, 2026, 1, null, null, null))).hasSize(2);
    }

    @Test
    void should_give_the_history_of_an_invoice_newest_first_and_nothing_for_an_unknown_one() {
        regler(F1, "100.00", "2026-02-01");
        regler(F1, "200.00", "2026-03-01");

        assertThat(consulter.historique(CENTRE, F1)).extracting(r -> r.montant().toPlainString()).containsExactly("200.00", "100.00");
        assertThat(consulter.historique(CENTRE, UUID.randomUUID())).isEmpty();
        assertThat(consulter.historique(new CentreId(UUID.randomUUID()), F1)).isEmpty();
        assertThat(consulter.rechercher(new CriteresDeSituation(CENTRE, 2026, 1, null, null, null), 0, 20).items().getFirst().etat())
                .isIn(EtatDeReglement.NON_REGLEE, EtatDeReglement.PARTIELLEMENT_REGLEE);
    }
}
