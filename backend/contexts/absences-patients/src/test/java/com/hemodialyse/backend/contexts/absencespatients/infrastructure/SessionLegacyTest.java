package com.hemodialyse.backend.contexts.absencespatients.infrastructure;

import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyidentite.SessionLegacy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Garde de centre de l'ACL de session (RG-ABS-023, RG-TRV-002) : centre de la session imposé, centre étranger refusé, SUPERADMIN
 * transverse, session sans centre ou illisible refusée.
 */
class SessionLegacyTest {

    private static final UUID CENTRE = new UUID(1, 1);
    private static final UUID AUTRE = new UUID(1, 2);
    private final SessionLegacy session = new SessionLegacy();

    /** Principal de session minimal : seul l'accesseur {@code getCenterId()} compte pour l'ACL. */
    public record Principal(String centreId) {
        public String getCenterId() {
            return centreId;
        }
    }

    private static void connecte(Object principal, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void deconnecte() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void le_centre_de_la_session_est_retenu_avec_ou_sans_centre_demande() {
        connecte(new Principal(CENTRE.toString()), "ROLE_ADMIN");

        assertEquals(CENTRE, session.centre(null).valeur());
        assertEquals(CENTRE, session.centre(CENTRE).valeur());
    }

    @Test
    void un_autre_centre_est_refuse() {
        connecte(new Principal(CENTRE.toString()), "ROLE_ADMIN");

        assertThrows(AccessDeniedException.class, () -> session.centre(AUTRE));
    }

    @Test
    void le_superadmin_est_transverse_mais_doit_designer_un_centre() {
        connecte(new Principal(CENTRE.toString()), "ROLE_SUPERADMIN");
        assertEquals(AUTRE, session.centre(AUTRE).valeur());
        assertEquals(CENTRE, session.centre(null).valeur());

        connecte(new Principal(null), "ROLE_SUPERADMIN");
        assertThrows(AccessDeniedException.class, () -> session.centre(null));
    }

    @Test
    void une_session_sans_centre_ou_au_centre_illisible_est_refusee() {
        connecte(new Principal(" "), "ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> session.centre(null));

        connecte(new Principal("pas-un-uuid"), "ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> session.centre(null));
    }

    @Test
    void sans_authentification_ou_avec_un_principal_inattendu_l_acces_est_refuse() {
        assertThrows(AccessDeniedException.class, () -> session.centre(null));

        connecte("quelqu'un", "ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> session.centre(null));
    }

    /** Principal avec identité : accesseurs {@code getCenterId()} et {@code getId()}. */
    public record PrincipalIdentifie(String centreId, String id) {
        public String getCenterId() {
            return centreId;
        }

        public String getId() {
            return id;
        }
    }

    @Test
    void l_intervenant_est_l_utilisateur_de_la_session_avec_le_droit_de_correction_de_l_administrateur_et_du_medecin() {
        UUID utilisateur = new UUID(5, 5);
        for (String role : List.of("ROLE_ADMIN", "ROLE_MEDECIN")) {
            connecte(new PrincipalIdentifie(CENTRE.toString(), utilisateur.toString()), role);
            var intervenant = session.intervenant();
            assertEquals(utilisateur, intervenant.utilisateur().valeur());
            assertEquals(com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection.ACCORDE, intervenant.droit());
        }
        for (String role : List.of("ROLE_SECRETAIRE", "ROLE_INFIRMIER")) {
            connecte(new PrincipalIdentifie(CENTRE.toString(), utilisateur.toString()), role);
            assertEquals(com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection.REFUSE, session.intervenant().droit());
        }
    }

    @Test
    void l_intervenant_sans_identite_lisible_n_a_pas_d_utilisateur_et_sans_session_il_est_refuse() {
        connecte(new Principal(CENTRE.toString()), "ROLE_ADMIN");
        assertEquals(null, session.intervenant().utilisateur());
        connecte(new PrincipalIdentifie(CENTRE.toString(), "pas-un-uuid"), "ROLE_ADMIN");
        assertEquals(null, session.intervenant().utilisateur());
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, session::intervenant);
    }
}
