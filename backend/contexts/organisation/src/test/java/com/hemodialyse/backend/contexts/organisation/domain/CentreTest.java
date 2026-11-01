package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CentreTest {

    private static final SocieteId SOCIETE = new SocieteId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));

    private static Centre centreValide() {
        return new Centre(new CentreId(UUID.randomUUID()), SOCIETE, "C1", "Centre 1",
                new Coordonnees("Rue", "Alger", null, null, null, null), "Africa/Algiers", true);
    }

    @Test
    void creation_valide() {
        Centre c = centreValide();
        assertThat(c.code()).isEqualTo("C1");
        assertThat(c.fuseauHoraire()).isEqualTo("Africa/Algiers");
        assertThat(c.coordonnees().ville()).isEqualTo("Alger");
    }

    @Test
    void fuseau_defaut_utc() {
        Centre c = new Centre(new CentreId(UUID.randomUUID()), SOCIETE, "C1", "Centre 1",
                null, null, true);
        assertThat(c.fuseauHoraire()).isEqualTo("UTC");
        assertThat(c.coordonnees()).isEqualTo(Coordonnees.VIDES);
    }

    @Test
    void rejeter_code_invalide() {
        assertThatThrownBy(() -> new Centre(new CentreId(UUID.randomUUID()), SOCIETE, "x", "Centre 1",
                null, null, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CENTRE_CODE_INVALIDE");
    }

    @Test
    void rejeter_nom_invalide() {
        assertThatThrownBy(() -> new Centre(new CentreId(UUID.randomUUID()), SOCIETE, "C1", "  ",
                null, null, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CENTRE_NOM_INVALIDE");
    }

    @Test
    void modifier_parametres() {
        Centre c = centreValide();
        c.modifierParametres("Nouveau nom", null, "Europe/Paris", false);
        assertThat(c.nom()).isEqualTo("Nouveau nom");
        assertThat(c.fuseauHoraire()).isEqualTo("Europe/Paris");
        assertThat(c.actif()).isFalse();
    }

    @Test
    void transferer() {
        Centre c = centreValide();
        SocieteId cible = new SocieteId(UUID.randomUUID());
        c.transferer(cible);
        assertThat(c.societeId()).isEqualTo(cible);
    }
}
