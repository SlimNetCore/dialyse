package com.hemodialyse.backend.contexts.facturation.application.parametres;

import com.hemodialyse.backend.contexts.facturation.doubles.ParametresEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotationInvalide;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurerLaFacturationTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());
    private static final Instant MAINTENANT = Instant.parse("2026-10-19T10:00:00Z");

    private final ParametresEnMemoire parametres = new ParametresEnMemoire();
    private final ConfigurerLaFacturation configurer = new ConfigurerLaFacturation(parametres, new UniteDeTravailDirecte(),
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    @Test
    void un_centre_sans_parametres_lit_les_valeurs_par_defaut_sans_rien_ecrire() {
        ParametresDeFacturation lus = configurer.consulter(CENTRE);

        assertThat(lus).isEqualTo(ParametresDeFacturation.PAR_DEFAUT);
        assertThat(parametres.ecritures()).isZero();
        assertThat(parametres.duCentre(CENTRE)).isEmpty();
    }

    @Test
    void la_modification_enregistre_les_valeurs_l_instant_et_l_auteur_puis_se_relit() {
        ParametresDeFacturation modifies = configurer.modifier(CENTRE,
                new ConfigurerLaFacturation.Commande("F{YY}-{CENTER}-{SEQ6}", false), "admin");

        assertThat(modifies.modele().valeur()).isEqualTo("F{YY}-{CENTER}-{SEQ6}");
        assertThat(modifies.regroupementMultiForfait()).isFalse();
        assertThat(modifies.misAJourLe()).isEqualTo(MAINTENANT);
        assertThat(parametres.auteur(CENTRE)).isEqualTo("admin");
        assertThat(configurer.consulter(CENTRE)).isEqualTo(modifies);
        assertThat(configurer.consulter(AUTRE_CENTRE)).isEqualTo(ParametresDeFacturation.PAR_DEFAUT);
    }

    @Test
    void un_modele_invalide_est_refuse_sans_rien_ecrire() {
        assertThatThrownBy(() -> configurer.modifier(CENTRE, new ConfigurerLaFacturation.Commande("FACTURE", true), "admin"))
                .isInstanceOf(ModeleDeNumerotationInvalide.class);
        assertThatThrownBy(() -> configurer.modifier(CENTRE, new ConfigurerLaFacturation.Commande("FAC-{MOIS}-{SEQ}", true),
                "admin")).isInstanceOf(ModeleDeNumerotationInvalide.class);
        assertThat(parametres.ecritures()).isZero();
    }
}
