package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Épingle les formes d'expression {@code @PreAuthorize} utilisées par les contrôleurs du contexte et le refus explicite des autres. */
class DroitsDeLaRouteTest {

    private static Authentication avec(String... roles) {
        String[] autorites = new String[roles.length];
        for (int i = 0; i < roles.length; i++) autorites[i] = "ROLE_" + roles[i];
        return new TestingAuthenticationToken("utilisateur", "x", autorites);
    }

    @Test
    void hasRole_accepte_le_seul_role_demande() {
        assertThat(DroitsDeLaRoute.suffisants("hasRole('MEDECIN')", avec("MEDECIN"))).isTrue();
        assertThat(DroitsDeLaRoute.suffisants("hasRole('MEDECIN')", avec("ADMIN"))).isFalse();
        assertThat(DroitsDeLaRoute.suffisants("hasRole('MEDECIN')", avec("INFIRMIER"))).isFalse();
    }

    @Test
    void hasAnyRole_accepte_un_des_roles_de_la_liste() {
        String lecture = "hasAnyRole('MEDECIN','ADMIN')";
        assertThat(DroitsDeLaRoute.suffisants(lecture, avec("ADMIN"))).isTrue();
        assertThat(DroitsDeLaRoute.suffisants(lecture, avec("MEDECIN"))).isTrue();
        assertThat(DroitsDeLaRoute.suffisants(lecture, avec("INFIRMIER"))).isFalse();
        assertThat(DroitsDeLaRoute.suffisants("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')", avec("INFIRMIER"))).isTrue();
        assertThat(DroitsDeLaRoute.suffisants("hasAnyRole('INFIRMIER','MEDECIN')", avec("ADMIN"))).isFalse();
    }

    @Test
    void une_expression_composee_est_evaluee_et_non_lue_par_motif() {
        // Un motif lirait 'ADMIN' dans la négation et accorderait à tort ; l'évaluation respecte l'opérateur.
        assertThat(DroitsDeLaRoute.suffisants("!hasRole('ADMIN')", avec("ADMIN"))).isFalse();
        assertThat(DroitsDeLaRoute.suffisants("hasRole('MEDECIN') and hasRole('ADMIN')", avec("MEDECIN"))).isFalse();
    }

    @Test
    void sans_authentification_la_route_est_refusee() {
        assertThat(DroitsDeLaRoute.suffisants("hasRole('MEDECIN')", null)).isFalse();
    }

    @Test
    void une_forme_inconnue_echoue_clairement_sans_accord_ni_refus_silencieux() {
        assertThatThrownBy(() -> DroitsDeLaRoute.suffisants("@garde.autorise(#patientId)", avec("MEDECIN")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non prise en charge");
        assertThatThrownBy(() -> DroitsDeLaRoute.suffisants("hasRole(", avec("MEDECIN")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non prise en charge");
        assertThatThrownBy(() -> DroitsDeLaRoute.suffisants("'MEDECIN'", avec("MEDECIN")))
                .isInstanceOf(RuntimeException.class);
    }
}
