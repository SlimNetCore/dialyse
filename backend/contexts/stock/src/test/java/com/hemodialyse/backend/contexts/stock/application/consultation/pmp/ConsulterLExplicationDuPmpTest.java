package com.hemodialyse.backend.contexts.stock.application.consultation.pmp;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.EtapeExpliquee;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.ExplicationDuPmp;
import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsulterLExplicationDuPmpTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UUID ARTICLE = UUID.randomUUID();

    private final AtomicInteger rejeux = new AtomicInteger();

    private ExplicationDuPmp rejeu(int etapes) {
        rejeux.incrementAndGet();
        List<EtapeExpliquee> liste = IntStream.range(0, etapes).mapToObj(i -> new EtapeExpliquee(null, "BR-" + i, null, "ENTREE",
                BigDecimal.ONE, BigDecimal.TEN, null, null, null, null, null, null, "f" + i)).toList();
        return new ExplicationDuPmp(ARTICLE, "A1", "Article", "PMP", liste, new BigDecimal("25"), new BigDecimal("250"), BigDecimal.TEN);
    }

    @Test
    void le_rejeu_complet_est_decoupe_en_memoire_et_le_resume_accompagne_la_page() {
        var explication = new ConsulterLExplicationDuPmp((c, a) -> rejeu(25));
        var page = explication.page(CENTRE, ARTICLE, 1, 10, null);
        assertEquals(List.of("BR-10", "BR-19"), List.of(page.etapes().items().get(0).piece(), page.etapes().items().get(9).piece()));
        assertEquals(25, page.etapes().total());
        assertEquals(new BigDecimal("25"), page.explication().quantiteFinale());
        var fin = explication.page(CENTRE, ARTICLE, 3, 10, null);
        assertEquals(0, fin.etapes().items().size());
        assertEquals(25, fin.etapes().total());
    }

    @Test
    void aucun_tri_accepte_et_parametres_verifies_avant_le_rejeu() {
        var explication = new ConsulterLExplicationDuPmp((c, a) -> rejeu(3));
        assertEquals(PaginationInvalide.TRI_INVALIDE,
                assertThrows(PaginationInvalide.class, () -> explication.page(CENTRE, ARTICLE, 0, 20, "date,desc")).code());
        assertEquals(PaginationInvalide.TAILLE_INVALIDE,
                assertThrows(PaginationInvalide.class, () -> explication.page(CENTRE, ARTICLE, 0, 0, null)).code());
        assertEquals(0, rejeux.get());
    }
}
