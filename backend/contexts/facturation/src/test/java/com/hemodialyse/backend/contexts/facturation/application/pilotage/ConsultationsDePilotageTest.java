package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;

import com.hemodialyse.backend.contexts.facturation.doubles.PilotageEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.PilotageEnMemoire.Facture;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage de lecture et d'impression du pilotage, avec les doubles en mémoire.
 */
class ConsultationsDePilotageTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final LocalDate DEBUT = LocalDate.of(2026, 2, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 2, 28);

    private final PilotageEnMemoire donnees = new PilotageEnMemoire();
    private final List<Object[]> impressions = new ArrayList<>();
    private final ImprimerLaSynthese imprimer = new ImprimerLaSynthese(donnees, (synthese, format) -> {
        impressions.add(new Object[]{synthese, format});
        return new byte[]{1, 2, 3};
    });

    private void uneFacture(String ttc) {
        donnees.poserLaFacture(new Facture(UUID.randomUUID(), CENTRE.valeur(), UUID.randomUUID(), LocalDate.of(2026, 2, 10),
                new BigDecimal("100.00"), new BigDecimal("19.00"), new BigDecimal(ttc), "CNAS", "CNAS Centre", "PERMANENT"));
    }

    @Test
    void should_require_the_month_to_consult_the_dashboard() {
        assertThatThrownBy(() -> new ConsulterLeTableauDeBord(donnees).executer(CENTRE, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le mois est obligatoire");
    }

    @Test
    void should_sum_the_stored_totals_of_the_month_in_the_dashboard() {
        uneFacture("119.00");
        uneFacture("119.01");

        TableauDeBordDuMois tableau = new ConsulterLeTableauDeBord(donnees).executer(CENTRE, YearMonth.of(2026, 2));

        assertThat(tableau.chiffreDAffairesTtc()).isEqualByComparingTo("238.01");
        assertThat(tableau.chiffreDAffairesHt()).isEqualByComparingTo("200.00");
        assertThat(tableau.facturesEmises()).isEqualTo(2);
    }

    @Test
    void should_print_the_synthesis_of_the_period_with_the_requested_format() {
        uneFacture("119.00");

        byte[] document = imprimer.executer(CENTRE, DEBUT, FIN, " xlsx ");

        assertThat(document).containsExactly(1, 2, 3);
        SyntheseMensuelle synthese = (SyntheseMensuelle) impressions.get(0)[0];
        assertThat(synthese.totalTtc()).isEqualByComparingTo("119.00");
        assertThat(synthese.totalTva()).isEqualByComparingTo("19.00");
        assertThat(impressions.get(0)[1]).isEqualTo(FormatDImpression.XLSX);
    }

    @Test
    void should_refuse_an_incomplete_period_before_reading_anything() {
        assertThatThrownBy(() -> imprimer.executer(CENTRE, null, FIN, "PDF")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("periodStart et periodEnd sont obligatoires");
        assertThatThrownBy(() -> imprimer.executer(CENTRE, DEBUT, null, "PDF")).isInstanceOf(IllegalArgumentException.class);
        assertThat(impressions).isEmpty();
    }

    @Test
    void should_refuse_a_reversed_period_but_accept_a_single_day() {
        assertThatThrownBy(() -> imprimer.executer(CENTRE, FIN, DEBUT, "PDF")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("periodStart doit etre inferieure ou egale a periodEnd");

        imprimer.executer(CENTRE, FIN, FIN, null);

        assertThat(impressions).hasSize(1);
        assertThat(impressions.get(0)[1]).isEqualTo(FormatDImpression.PDF);
    }

    @Test
    void should_refuse_an_unknown_format_without_printing() {
        assertThatThrownBy(() -> imprimer.executer(CENTRE, DEBUT, FIN, "docx")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("format non supporte: DOCX");
        assertThat(impressions).isEmpty();
    }

    @Test
    void should_read_the_synthesis_without_printing_for_the_document_template_route() {
        uneFacture("119.00");

        SyntheseMensuelle synthese = new ConsulterLaSynthese(donnees).executer(CENTRE, DEBUT, FIN);

        assertThat(synthese.totalHt()).isEqualByComparingTo("100.00");
        assertThat(impressions).isEmpty();
    }
}
