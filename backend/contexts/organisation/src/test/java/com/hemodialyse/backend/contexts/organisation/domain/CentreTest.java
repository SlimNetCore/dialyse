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
        c.modifierParametres(null, "Nouveau nom", null, "Europe/Paris", false);
        assertThat(c.nom()).isEqualTo("Nouveau nom");
        assertThat(c.fuseauHoraire()).isEqualTo("Europe/Paris");
        assertThat(c.actif()).isFalse();
    }

    private static Centre autreCentre() {
        return new Centre(new CentreId(UUID.randomUUID()), SOCIETE, "C2", "Centre 2", null, null, true);
    }

    private static Societe societe(SocieteId id, boolean actif) {
        return new Societe(id, "S1", "Société", null, null, null, null, null, actif, java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void should_deactivate_a_centre_when_another_centre_of_the_company_stays_active() {
        Centre c = centreValide();
        c.desactiver(java.util.List.of(c, autreCentre()));
        assertThat(c.actif()).isFalse();
    }

    @Test
    void should_refuse_to_deactivate_the_last_active_centre_of_the_company() {
        Centre c = centreValide();
        assertThatThrownBy(() -> c.desactiver(java.util.List.of(c)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("SOCIETE_DERNIER_CENTRE");
        assertThat(c.actif()).isTrue();
    }

    @Test
    void should_transfer_a_centre_to_another_active_company() {
        Centre c = centreValide();
        SocieteId cible = new SocieteId(UUID.randomUUID());
        c.transfererVers(societe(cible, true), java.util.List.of(c, autreCentre()));
        assertThat(c.societeId()).isEqualTo(cible);
    }

    @Test
    void should_refuse_a_transfer_to_the_same_company_to_an_inactive_company_or_of_the_last_active_centre() {
        Centre c = centreValide();
        SocieteId cible = new SocieteId(UUID.randomUUID());
        assertThatThrownBy(() -> c.transfererVers(societe(SOCIETE, true), java.util.List.of(c, autreCentre())))
                .hasMessage("CENTRE_TRANSFERT_MEME_SOCIETE");
        assertThatThrownBy(() -> c.transfererVers(societe(cible, false), java.util.List.of(c, autreCentre())))
                .hasMessage("SOCIETE_INACTIVE");
        assertThatThrownBy(() -> c.transfererVers(societe(cible, true), java.util.List.of(c)))
                .hasMessage("SOCIETE_DERNIER_CENTRE");
        assertThat(c.societeId()).isEqualTo(SOCIETE);
    }

    @Test
    void transferer() {
        Centre c = centreValide();
        SocieteId cible = new SocieteId(UUID.randomUUID());
        c.transferer(cible);
        assertThat(c.societeId()).isEqualTo(cible);
    }
}
