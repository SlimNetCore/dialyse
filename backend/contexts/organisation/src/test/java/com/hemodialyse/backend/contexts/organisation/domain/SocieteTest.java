package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocieteTest {

    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);

    private static Societe societeValide() {
        return new Societe(new SocieteId(UUID.randomUUID()), "RENADIAL", "Renadial",
                "12345678901234", "12345", "RC-ALG-1",
                new Coordonnees("Rue des Frères", "Alger", "Alger", "+213555010203",
                        "contact@renadial.dz", "https://renadial.dz"),
                "Pied de page", true, MAINTENANT);
    }

    @Test
    void creation_valide() {
        Societe s = societeValide();
        assertThat(s.code()).isEqualTo("RENADIAL");
        assertThat(s.nif()).isEqualTo("12345678901234");
        assertThat(s.coordonnees().ville()).isEqualTo("Alger");
        assertThat(s.piedDePage()).isEqualTo("Pied de page");
        assertThat(s.actif()).isTrue();
    }

    @Test
    void creation_invalide() {
        assertThatThrownBy(() -> new Societe(null, "RENADIAL", "Renadial", null, null, null,
                null, null, true, MAINTENANT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Societe(new SocieteId(UUID.randomUUID()), "a", "Renadial",
                null, null, null, null, null, true, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SOCIETE_CODE_INVALIDE");
        assertThatThrownBy(() -> new Societe(new SocieteId(UUID.randomUUID()), "RENADIAL", "  ",
                null, null, null, null, null, true, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SOCIETE_NOM_INVALIDE");
    }

    @Test
    void identifiants_legaux_limites_a_40_caracteres() {
        String tropLong = "X".repeat(41);
        assertThatThrownBy(() -> new Societe(new SocieteId(UUID.randomUUID()), "RENADIAL", "Renadial",
                tropLong, null, null, null, null, true, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SOCIETE_IDENTIFIANT_INVALIDE");
    }

    @Test
    void pied_de_page_limite_a_500_caracteres() {
        assertThatThrownBy(() -> new Societe(new SocieteId(UUID.randomUUID()), "RENADIAL", "Renadial",
                null, null, null, null, "Y".repeat(501), true, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SOCIETE_PIED_PAGE_TROP_LONG");
    }

    @Test
    void modification() {
        Societe s = societeValide();
        s.modifier("RENADIAL2", "Renadial 2", null, null, null, null, null, false);
        assertThat(s.code()).isEqualTo("RENADIAL2");
        assertThat(s.actif()).isFalse();
    }
}
