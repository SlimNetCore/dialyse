package com.hemodialyse.backend.contexts.stock.domain.groupes;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Groupe d'articles : nom, description, composition (RG-STK-070, 071).
 */
class GroupeDArticlesTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final OffsetDateTime T = OffsetDateTime.of(2026, 10, 23, 8, 0, 0, 0, ZoneOffset.UTC);

    private static GroupeDArticles groupe(String nom, String description, List<UUID> articles) {
        return GroupeDArticles.composer(UUID.randomUUID(), CENTRE, nom, description, articles, T);
    }

    private static String refus(Runnable r) {
        return assertThrows(GroupeDArticlesInvalide.class, r::run).getMessage();
    }

    @Test
    void la_cle_ignore_casse_accents_et_espaces_multiples() {
        assertEquals("kit cnas", GroupeDArticles.cle("  Kït   CNÀS "));
        assertEquals(GroupeDArticles.cle("Kit CNAS"), GroupeDArticles.cle("KIT  cnas"));
        assertEquals("", GroupeDArticles.cle(null));
    }

    @Test
    void le_nom_et_la_description_sont_nettoyes_et_la_composition_sans_doublon_ni_nul() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000001");
        GroupeDArticles g = groupe("  Kit   CNAS ", "   ", Arrays.asList(a, null, b, a));
        assertEquals("Kit CNAS", g.nom());
        assertEquals("kit cnas", g.cle());
        assertNull(g.description());
        assertEquals(List.of(b, a), List.copyOf(g.articles()));
        assertEquals(T, g.modifieLe());
    }

    @Test
    void le_nom_est_obligatoire_et_borne_a_100_caracteres_apres_nettoyage() {
        UUID a = UUID.randomUUID();
        assertEquals("Nom du groupe requis", refus(() -> groupe("  ", null, List.of(a))));
        assertEquals("Nom trop long (100 caractères maximum)", refus(() -> groupe("N".repeat(101), null, List.of(a))));
        assertEquals(100, groupe(" " + "N".repeat(100) + " ", null, List.of(a)).nom().length());
    }

    @Test
    void la_description_est_bornee_a_500_caracteres() {
        UUID a = UUID.randomUUID();
        assertEquals("Description trop longue (500 caractères maximum)", refus(() -> groupe("K", "D".repeat(501), List.of(a))));
        assertEquals(500, groupe("K", "D".repeat(500), List.of(a)).description().length());
    }

    @Test
    void un_groupe_compte_de_1_a_500_articles() {
        List<UUID> articles = new ArrayList<>();
        IntStream.range(0, 501).forEach(i -> articles.add(UUID.randomUUID()));
        assertEquals("Un groupe doit contenir au moins un article", refus(() -> groupe("K", null, List.of())));
        assertEquals("Un groupe doit contenir au moins un article", refus(() -> groupe("K", null, Arrays.asList((UUID) null))));
        assertEquals("Un groupe ne peut pas dépasser 500 articles", refus(() -> groupe("K", null, articles)));
        assertEquals(500, groupe("K", null, articles.subList(0, 500)).articles().size());
    }

    @Test
    void la_modification_garde_identifiant_et_centre() {
        UUID a = UUID.randomUUID();
        GroupeDArticles g = groupe("K", "d", List.of(a));
        GroupeDArticles m = g.modifier("L", null, List.of(a), T.plusDays(1));
        assertEquals(g.id(), m.id());
        assertEquals(g.centre(), m.centre());
        assertEquals("L", m.nom());
        assertEquals(T.plusDays(1), m.modifieLe());
    }
}

