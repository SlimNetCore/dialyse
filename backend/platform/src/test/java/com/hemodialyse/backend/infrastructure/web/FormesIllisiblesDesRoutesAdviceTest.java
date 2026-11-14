package com.hemodialyse.backend.infrastructure.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/** DM8 (ADR 0087) : sur les indicateurs médicaux de la Direction, le 403 d'un profil sans droit passe AVANT le 400 d'une période illisible. */
class FormesIllisiblesDesRoutesAdviceTest {

    @PreAuthorize("hasRole('DIRECTION')")
    static class RouteDeLaDirection {
        public void indicateurs(String from) {
        }
    }

    private final FormesIllisiblesDesRoutesAdvice advice = new FormesIllisiblesDesRoutesAdvice(new ApiExceptionHandler());

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private ProblemDetail periodeIllisibleSur(String chemin, String... roles) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
        var requete = new MockHttpServletRequest("GET", chemin);
        requete.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new RouteDeLaDirection(), RouteDeLaDirection.class.getMethod("indicateurs", String.class)));
        var erreur = new MethodArgumentTypeMismatchException("hier", java.time.LocalDate.class, "from", null, null);
        return advice.parametreIllisible(erreur, requete);
    }

    /**
     * PL10b1 (ADR 0151) et PL10b2 (ADR 0152) : les neuf routes de la Direction sont servies par le Pilotage, qui lit la garde AVANT les
     * paramètres (403 avant 400, KB-PL10b-1) : cet advice n'y répond plus, la réponse reste celle du gestionnaire commun. Leurs 400/403 sont
     * verrouillés par la caractérisation PL10b.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/direction/gmao", "/api/v1/direction/capacite", "/api/v1/direction/absences",
            "/api/v1/direction/stock-groupes", "/api/v1/direction/indicators"})
    void les_routes_de_la_direction_ne_sont_plus_reprises_par_cet_advice(String chemin) throws Exception {
        ProblemDetail reponse = periodeIllisibleSur(chemin, "ROLE_DIRECTION");

        assertThat(reponse.getDetail()).isNotEqualTo("Paramètre illisible : from");
    }

    // ---- RF5 (revue complémentaire RF1-RF3d, constat 5) : refus par défaut sur l'administration générique

    static class RouteSansGarde {
        public void caisses(String centerId) {
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    static class RouteDeLAdministration {
        public void caisses(String centerId) {
        }
    }

    private ProblemDetail parametreAbsentSurLAdministration(Object route, Class<?> type, String... roles) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
        var requete = new MockHttpServletRequest("GET", "/api/v1/admin/referentials/caisses");
        if (route != null) {
            requete.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                    new HandlerMethod(route, type.getMethod("caisses", String.class)));
        }
        return advice.parametreAbsent(new org.springframework.web.bind.MissingServletRequestParameterException("centerId", "UUID"), requete);
    }

    @Test
    void une_route_de_l_administration_sans_garde_est_refusee_par_defaut_en_403_et_non_en_400() throws Exception {
        ProblemDetail reponse = parametreAbsentSurLAdministration(new RouteSansGarde(), RouteSansGarde.class, "ROLE_ADMIN");

        assertThat(reponse.getStatus()).isEqualTo(403);
        assertThat(reponse.getProperties()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void une_route_de_l_administration_sans_gestionnaire_connu_est_refusee_par_defaut_en_403() throws Exception {
        ProblemDetail reponse = parametreAbsentSurLAdministration(null, null, "ROLE_ADMIN");

        assertThat(reponse.getStatus()).isEqualTo(403);
    }

    @Test
    void le_motif_de_l_administration_est_ancre_au_chemin_sans_le_contexte_applicatif_constat_6() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", "ROLE_ADMIN"));
        var erreur = new org.springframework.web.bind.MissingServletRequestParameterException("centerId", "UUID");

        var avecContexte = new MockHttpServletRequest("GET", "/app/api/v1/admin/referentials/caisses");
        avecContexte.setContextPath("/app");
        avecContexte.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new RouteDeLAdministration(), RouteDeLAdministration.class.getMethod("caisses", String.class)));
        assertThat(advice.parametreAbsent(erreur, avecContexte).getStatus()).as("préfixé par le contexte : reconnu").isEqualTo(400);

        var fragmentAilleurs = new MockHttpServletRequest("GET", "/api/v1/autre/api/v1/admin/referentials/caisses");
        assertThat(advice.parametreAbsent(erreur, fragmentAilleurs).getDetail()).as("fragment au milieu du chemin : non reconnu, gestionnaire commun")
                .isNotEqualTo("Paramètre obligatoire absent : centerId");
    }

    @Test
    void une_route_gardee_de_l_administration_garde_ses_reponses_403_sans_droit_et_400_avec_droit() throws Exception {
        assertThat(parametreAbsentSurLAdministration(new RouteDeLAdministration(), RouteDeLAdministration.class, "ROLE_MEDECIN").getStatus())
                .isEqualTo(403);
        ProblemDetail avecDroit = parametreAbsentSurLAdministration(new RouteDeLAdministration(), RouteDeLAdministration.class, "ROLE_ADMIN");
        assertThat(avecDroit.getStatus()).isEqualTo(400);
        assertThat(avecDroit.getDetail()).isEqualTo("Paramètre obligatoire absent : centerId");
    }
}
