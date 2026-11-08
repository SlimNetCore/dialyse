package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.SortiesDuJourDuStock;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Traduction table-driven du langage publié du Stock vers les sorties du jour de la Séance (SE7a, KB-SEA-61). */
class TraducteursDeSe7aTest {

    @Test
    void should_translate_each_output_term_for_term_and_keep_the_order_of_the_stock() {
        var stock = Mockito.mock(ConsommationsDeSeance.class);
        UUID centre = new UUID(0, 0xA);
        LocalDate jour = LocalDate.of(2026, 6, 15);
        Mockito.when(stock.sortiesParDate(centre, jour)).thenReturn(List.of(
                new ConsommationsDeSeance.SortieParDate(new UUID(6, 1), "A-1", "Premier", new BigDecimal("3.0")),
                new ConsommationsDeSeance.SortieParDate(new UUID(6, 2), "A-2", "Second", new BigDecimal("4.5"))));

        var sorties = new SortiesDuJourDuStock(stock).du(new CentreId(centre), jour);

        assertThat(sorties).hasSize(2);
        assertThat(sorties.get(0).article()).isEqualTo(new UUID(6, 1));
        assertThat(sorties.get(0).code()).isEqualTo("A-1");
        assertThat(sorties.get(0).libelle()).isEqualTo("Premier");
        assertThat(sorties.get(0).quantiteTotale()).isEqualByComparingTo("3");
        assertThat(sorties.get(1).code()).isEqualTo("A-2");
    }

    @Test
    void should_give_no_output_when_the_stock_has_none() {
        var stock = Mockito.mock(ConsommationsDeSeance.class);
        Mockito.when(stock.sortiesParDate(Mockito.any(), Mockito.any())).thenReturn(List.of());

        assertThat(new SortiesDuJourDuStock(stock).du(new CentreId(new UUID(0, 0xA)), LocalDate.of(2026, 6, 15))).isEmpty();
    }
}
