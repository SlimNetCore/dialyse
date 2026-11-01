package com.hemodialyse.backend.contexts.organisation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordonneesTest {

    @Test
    void champs_vides_normalises_a_null() {
        Coordonnees c = new Coordonnees("  ", null, "", "   ", null, null);
        assertThat(c).isEqualTo(Coordonnees.VIDES);
    }

    @Test
    void email_minuscule_et_valide() {
        Coordonnees c = new Coordonnees(null, null, null, null, "Contact@Renadial.DZ", null);
        assertThat(c.email()).isEqualTo("contact@renadial.dz");
    }

    @Test
    void rejeter_email_invalide() {
        assertThatThrownBy(() -> new Coordonnees(null, null, null, null, "pas-un-email", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("COORDONNEES_EMAIL_INVALIDE");
    }

    @Test
    void rejeter_telephone_invalide() {
        assertThatThrownBy(() -> new Coordonnees(null, null, null, "abc", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("COORDONNEES_TELEPHONE_INVALIDE");
    }

    @Test
    void rejeter_site_web_invalide() {
        assertThatThrownBy(() -> new Coordonnees(null, null, null, null, null, "pas un site"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("COORDONNEES_SITE_WEB_INVALIDE");
    }

    @Test
    void rejeter_champ_trop_long() {
        assertThatThrownBy(() -> new Coordonnees("A".repeat(251), null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("COORDONNEES_TROP_LONG");
    }
}
