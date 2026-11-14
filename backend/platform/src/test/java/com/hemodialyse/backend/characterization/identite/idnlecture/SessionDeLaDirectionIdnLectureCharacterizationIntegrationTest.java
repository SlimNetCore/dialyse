package com.hemodialyse.backend.characterization.identite.idnlecture;

import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * IDN-lecture, session de la direction du Pilotage ({@code SessionLegacy}, ACL vers Identité et accès) : habilitation d'un compte à une
 * société (compte actif, rattaché, société active, relus en base) et refus de la session de la direction (rôle, société du jeton,
 * identifiants lisibles). Complète les gardes de routes de PL10b par la matrice directe de l'ACL.
 */
class SessionDeLaDirectionIdnLectureCharacterizationIntegrationTest extends IdnLectureSupport {

    private UUID alpha, beta, inactive;
    private UUID directionActive, directionInactive, directionDetachee, directionBeta, directionSocieteInactive;
    private SessionLegacy session;

    @Override
    protected String dossier() {
        return "pilotage";
    }

    @BeforeEach
    void monde() {
        alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        beta = societeNommee("SOCIETE_BETA", "BETA", "Beta Clinique", true);
        inactive = societeNommee("SOCIETE_INACTIVE", "INAC", "Zeta Inactive", false);
        directionActive = compte("DIRECTION_ACTIVE", "dir-active", true, "DIRECTION");
        rattacherALaSociete(directionActive, alpha);
        directionInactive = compte("DIRECTION_INACTIVE", "dir-inactive", false, "DIRECTION");
        rattacherALaSociete(directionInactive, alpha);
        directionDetachee = compte("DIRECTION_DETACHEE", "dir-detachee", true, "DIRECTION");
        directionBeta = compte("DIRECTION_BETA", "dir-beta", true, "DIRECTION");
        rattacherALaSociete(directionBeta, beta);
        directionSocieteInactive = compte("DIRECTION_SOCIETE_INACTIVE", "dir-soc-inactive", true, "DIRECTION");
        rattacherALaSociete(directionSocieteInactive, inactive);
        session = new SessionLegacy(jdbc, annuaireDesSocietes);
    }

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void l_habilitation_d_un_compte_a_une_societe_est_relue_en_base() {
        UUID inconnu = UUID.nameUUIDFromBytes("compte-inconnu".getBytes());
        traceNote("compte actif rattache a une societe active", String.valueOf(session.estHabilite(directionActive, alpha)));
        traceNote("compte actif rattache, autre societe demandee", String.valueOf(session.estHabilite(directionActive, beta)));
        traceNote("compte desactive", String.valueOf(session.estHabilite(directionInactive, alpha)));
        traceNote("compte sans rattachement", String.valueOf(session.estHabilite(directionDetachee, alpha)));
        traceNote("compte d'une autre societe", String.valueOf(session.estHabilite(directionBeta, alpha)));
        traceNote("societe desactivee", String.valueOf(session.estHabilite(directionSocieteInactive, inactive)));
        traceNote("compte inconnu", String.valueOf(session.estHabilite(inconnu, alpha)));
        traceNote("societe inconnue", String.valueOf(session.estHabilite(directionActive, UUID.nameUUIDFromBytes("societe-inconnue".getBytes()))));
        jdbc.update("UPDATE societes SET actif = FALSE WHERE id = ?", alpha);
        traceNote("societe desactivee depuis", String.valueOf(session.estHabilite(directionActive, alpha)));
        jdbc.update("UPDATE societes SET actif = TRUE WHERE id = ?", alpha);
        jdbc.update("UPDATE app_user SET active = FALSE WHERE id = ?", directionActive);
        traceNote("compte desactive depuis", String.valueOf(session.estHabilite(directionActive, alpha)));
        jdbc.update("UPDATE app_user SET active = TRUE WHERE id = ?", directionActive);
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", directionActive);
        traceNote("compte detache depuis", String.valueOf(session.estHabilite(directionActive, alpha)));
        verrouiller("habilitation");
    }

    @Test
    void la_session_de_la_direction_exige_le_role_la_societe_du_jeton_et_l_habilitation() {
        traceNote("aucune authentification", issue());
        authentifier(principal(directionActive, null, alpha.toString()), "ROLE_ADMIN");
        traceNote("role ADMIN seul", issue());
        authentifier("principal-sans-accesseurs", "ROLE_DIRECTION");
        traceNote("principal d'un autre type", issue());
        authentifier(principal(directionActive, null, null), "ROLE_DIRECTION");
        traceNote("jeton sans societe", issue());
        authentifier(principal(directionActive, null, "  "), "ROLE_DIRECTION");
        traceNote("jeton avec societe blanche", issue());
        authentifier(principal(directionActive, null, "pas-un-identifiant"), "ROLE_DIRECTION");
        traceNote("societe illisible", issue());
        authentifier(new UserPrincipal("pas-un-identifiant", null, "u", "", List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, alpha.toString()),
                "ROLE_DIRECTION");
        traceNote("compte illisible", issue());
        authentifier(principal(directionActive, null, alpha.toString()), "ROLE_DIRECTION");
        traceNote("compte habilite", issue());
        authentifier(principal(directionActive, UUID.nameUUIDFromBytes("centre".getBytes()).toString(), alpha.toString()), "ROLE_DIRECTION", "ROLE_ADMIN");
        traceNote("compte habilite, jeton avec centre et deux roles", issue());
        authentifier(principal(directionActive, null, beta.toString()), "ROLE_DIRECTION");
        traceNote("societe du jeton autre que celle du compte", issue());
        authentifier(principal(directionInactive, null, alpha.toString()), "ROLE_DIRECTION");
        traceNote("compte desactive", issue());
        authentifier(principal(directionDetachee, null, alpha.toString()), "ROLE_DIRECTION");
        traceNote("compte detache", issue());
        authentifier(principal(directionSocieteInactive, null, inactive.toString()), "ROLE_DIRECTION");
        traceNote("societe desactivee", issue());
        verrouiller("session");
    }

    private UserPrincipal principal(UUID compte, String centre, String societe) {
        return new UserPrincipal(compte.toString(), centre, "u", "", List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, societe);
    }

    private void authentifier(Object principal, String... roles) {
        List<SimpleGrantedAuthority> droits = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, droits));
    }

    /** Résultat de {@code societe()} : l'alias de la société, ou le type de l'exception et son message. */
    private String issue() {
        try {
            UUID societe = session.societe();
            return "societe " + aliasNommes.getOrDefault(societe.toString(), societe.toString());
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + " : " + e.getMessage();
        }
    }
}
