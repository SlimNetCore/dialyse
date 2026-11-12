package com.hemodialyse.backend.contexts.notifications.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceTest {

    @Test
    void une_audience_sans_role_est_visible_de_tous_meme_sans_aucun_role() {
        Audience tous = Audience.desRoles(List.of());
        assertThat(tous.estOuverteATous()).isTrue();
        assertThat(tous.inclut(Set.of(), "u1")).isTrue();
        assertThat(tous.inclut(Set.of("MEDECIN"), "u1")).isTrue();
    }

    @Test
    void une_audience_de_roles_n_inclut_que_les_utilisateurs_ayant_l_un_de_ces_roles() {
        Audience admins = Audience.desRoles(List.of("ADMIN", "SECRETAIRE"));
        assertThat(admins.inclut(Set.of("SECRETAIRE"), "u1")).isTrue();
        assertThat(admins.inclut(Set.of("MEDECIN", "ADMIN"), "u1")).isTrue();
        assertThat(admins.inclut(Set.of("MEDECIN"), "u1")).isFalse();
        assertThat(admins.inclut(Set.of(), "u1")).isFalse();
    }

    @Test
    void les_roles_se_comparent_exactement_et_le_souligne_n_est_pas_un_joker() {
        Audience reforme = Audience.desRoles(List.of("GMAOXREFORME"));
        assertThat(reforme.inclut(Set.of("GMAO_REFORME"), "u1")).isFalse();
        assertThat(Audience.desRoles(List.of("GMAO_REFORME")).inclut(Set.of("GMAO_REFORME"), "u1")).isTrue();
        assertThat(Audience.desRoles(List.of("ADMIN")).inclut(Set.of("ADM"), "u1")).isFalse();
    }

    @Test
    void le_destinataire_vise_voit_sa_notification_meme_sans_le_role_cible() {
        Audience perso = Audience.desRolesEtUnDestinataire(List.of("ADMIN"), "compte-inf");
        assertThat(perso.inclut(Set.of("INFIRMIER"), "compte-inf")).isTrue();
        assertThat(perso.inclut(Set.of("INFIRMIER"), "autre-compte")).isFalse();
        assertThat(perso.inclut(Set.of("ADMIN"), "autre-compte")).isTrue();
    }

    @Test
    void les_roles_blancs_sont_ignores_et_les_espaces_retires() {
        Audience a = Audience.desRoles(List.of(" ADMIN ", "", "  "));
        assertThat(a.roles()).containsExactly("ADMIN");
        assertThat(Audience.desRoles(List.of(" ", "")).estOuverteATous()).isTrue();
    }

    @Test
    void un_destinataire_blanc_n_est_pas_un_destinataire() {
        assertThat(Audience.desRolesEtUnDestinataire(List.of("ADMIN"), "  ").destinataire()).isNull();
        assertThat(Audience.desRolesEtUnDestinataire(List.of("ADMIN"), null).destinataire()).isNull();
    }

    @Test
    void les_roles_s_ecrivent_dans_l_ordre_separes_par_des_virgules() {
        assertThat(Audience.desRoles(List.of("ADMIN", "SECRETAIRE")).rolesEnTexte()).isEqualTo("ADMIN,SECRETAIRE");
        assertThat(Audience.desRoles(List.of()).rolesEnTexte()).isEmpty();
    }
}
