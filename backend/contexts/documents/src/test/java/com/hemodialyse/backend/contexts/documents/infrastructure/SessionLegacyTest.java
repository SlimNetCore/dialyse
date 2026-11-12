package com.hemodialyse.backend.contexts.documents.infrastructure;

import com.hemodialyse.backend.contexts.documents.application.AccesRefuse;
import com.hemodialyse.backend.contexts.documents.infrastructure.acl.legacyidentite.SessionLegacy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La garde de centre de la session, même règle que CenterAccessGuard de la plateforme (KB-DOC1-1 : elle s'applique à l'impression). */
class SessionLegacyTest {

    private static final UUID D = new UUID(0L, 0xdL);
    private static final UUID E = new UUID(0L, 0xeL);

    /** Principal d'une session : seuls getId() et getCenterId() sont lus, par réflexion. */
    public static final class Principal {
        private final String id;
        private final String centre;

        Principal(String id, String centre) {
            this.id = id;
            this.centre = centre;
        }

        public String getId() {
            return id;
        }

        public String getCenterId() {
            return centre;
        }
    }

    private final SessionLegacy session = new SessionLegacy();

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    private static void ouvrir(Object principal, String... roles) {
        var autorites = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", autorites));
    }

    @Test
    void should_give_the_centre_of_the_session_when_none_or_the_same_is_asked() {
        ouvrir(new Principal("u", D.toString()), "ROLE_MEDECIN");

        assertThat(session.exigerLeCentre(null).valeur()).isEqualTo(D);
        assertThat(session.exigerLeCentre(D).valeur()).isEqualTo(D);
    }

    @Test
    void should_refuse_another_centre_whatever_the_ordinary_role() {
        for (String role : List.of("ROLE_ADMIN", "ROLE_MEDECIN", "ROLE_INFIRMIER")) {
            ouvrir(new Principal("u", D.toString()), role);
            assertThatThrownBy(() -> session.exigerLeCentre(E)).isInstanceOf(AccesRefuse.class)
                    .hasMessage("Accès refusé : le centre demandé ne correspond pas à la session");
        }
    }

    @Test
    void should_let_a_superadmin_reach_any_centre_and_require_one_when_the_session_has_none() {
        ouvrir(new Principal("u", D.toString()), "ROLE_SUPERADMIN");
        assertThat(session.exigerLeCentre(E).valeur()).isEqualTo(E);

        ouvrir(new Principal("u", null), "ROLE_SUPERADMIN");
        assertThatThrownBy(() -> session.exigerLeCentre(null)).isInstanceOf(AccesRefuse.class).hasMessage("centerId requis pour un accès transverse");
        assertThat(session.exigerLeCentre(E).valeur()).isEqualTo(E);
    }

    @Test
    void should_refuse_a_session_without_a_centre_or_with_an_unreadable_one() {
        ouvrir(new Principal("u", null), "ROLE_ADMIN");
        assertThatThrownBy(() -> session.exigerLeCentre(null)).isInstanceOf(AccesRefuse.class).hasMessage("Session sans centre de rattachement");

        ouvrir(new Principal("u", "pas-un-uuid"), "ROLE_ADMIN");
        assertThatThrownBy(() -> session.exigerLeCentre(null)).isInstanceOf(AccesRefuse.class)
                .hasMessage("Centre de rattachement illisible dans la session");
    }

    @Test
    void should_refuse_an_absent_or_foreign_authentication() {
        assertThatThrownBy(() -> session.exigerLeCentre(D)).isInstanceOf(AccesRefuse.class)
                .hasMessage("Contexte d'authentification invalide : centre de rattachement introuvable");

        ouvrir("un simple nom", "ROLE_ADMIN");
        assertThatThrownBy(() -> session.exigerLeCentre(D)).isInstanceOf(AccesRefuse.class)
                .hasMessage("Contexte d'authentification invalide : centre de rattachement introuvable");
    }
}
