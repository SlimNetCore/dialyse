package com.hemodialyse.backend.domain.stock.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GroupeArticleTest {

    private static final UUID CENTRE = UUID.randomUUID();

    @Test
    void should_create_a_named_group_deduplicating_articles() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        GroupeArticle g = GroupeArticle.creer(CENTRE, "  KIT   CNAS ", "  Kit de dialyse  ", List.of(a, b, a));

        assertEquals("KIT CNAS", g.nom());
        assertEquals("Kit de dialyse", g.description());
        assertEquals(2, g.articleIds().size());
        assertEquals(CENTRE, g.centerId());
    }

    @Test
    void should_consolidate_names_regardless_of_case_accents_and_spacing() {
        assertEquals(GroupeArticle.cleNom("Kit CNAS"), GroupeArticle.cleNom("KIT   cnas"));
        assertEquals("kit cnas", GroupeArticle.cleNom("  Kit  CNAS "));
        assertEquals(GroupeArticle.cleNom("Médicaments"), GroupeArticle.cleNom("MEDICAMENTS"));
        assertNotEquals(GroupeArticle.cleNom("Kit CNAS"), GroupeArticle.cleNom("Kit CASNOS"));
    }

    @Test
    void should_reject_invalid_groups() {
        UUID a = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> GroupeArticle.creer(CENTRE, " ", null, List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> GroupeArticle.creer(CENTRE, "X", null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> GroupeArticle.creer(null, "X", null, List.of(a)));
        assertThrows(IllegalArgumentException.class,
                () -> GroupeArticle.creer(CENTRE, "x".repeat(GroupeArticle.NOM_MAX + 1), null, List.of(a)));
        assertThrows(IllegalArgumentException.class,
                () -> GroupeArticle.creer(CENTRE, "X", "d".repeat(GroupeArticle.DESCRIPTION_MAX + 1), List.of(a)));
    }

    @Test
    void modifier_should_keep_identity_and_center_and_revalidate() {
        UUID a = UUID.randomUUID();
        GroupeArticle g = GroupeArticle.creer(CENTRE, "Kit", null, List.of(a));

        GroupeArticle m = g.modifier("Kit CNAS", "desc", List.of(a, UUID.randomUUID()));

        assertEquals(g.id(), m.id());
        assertEquals(CENTRE, m.centerId());
        assertEquals(2, m.articleIds().size());
        assertThrows(IllegalArgumentException.class, () -> g.modifier("", null, List.of(a)));
    }
}
