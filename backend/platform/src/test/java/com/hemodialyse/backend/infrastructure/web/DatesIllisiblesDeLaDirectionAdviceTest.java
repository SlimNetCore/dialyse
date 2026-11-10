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
class DatesIllisiblesDeLaDirectionAdviceTest {

    @PreAuthorize("hasRole('DIRECTION')")
    static class RouteDeLaDirection {
        public void indicateurs(String from) {
        }
    }

    private final DatesIllisiblesDeLaDirectionAdvice advice = new DatesIllisiblesDeLaDirectionAdvice(new ApiExceptionHandler());

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private ProblemDetail periodeIllisibleAvec(String... roles) throws Exception {
        return periodeIllisibleSur("/api/v1/direction/indicators", roles);
    }

    private ProblemDetail periodeIllisibleSur(String chemin, String... roles) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
        var requete = new MockHttpServletRequest("GET", chemin);
        requete.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new RouteDeLaDirection(), RouteDeLaDirection.class.getMethod("indicateurs", String.class)));
        var erreur = new MethodArgumentTypeMismatchException("hier", java.time.LocalDate.class, "from", null, null);
        return advice.parametreIllisible(erreur, requete);
    }

    @Test
    void un_profil_sans_droit_recoit_403_avant_le_400_d_une_periode_illisible() throws Exception {
        ProblemDetail reponse = periodeIllisibleAvec("ROLE_MEDECIN");
        assertThat(reponse.getStatus()).isEqualTo(403);
        assertThat(reponse.getProperties()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void le_profil_de_la_direction_recoit_400_pour_une_periode_illisible() throws Exception {
        ProblemDetail reponse = periodeIllisibleAvec("ROLE_DIRECTION");
        assertThat(reponse.getStatus()).isEqualTo(400);
        assertThat(reponse.getProperties()).containsEntry("code", "BAD_REQUEST");
    }

    /** KB-DIR-02 (lot L6) : mêmes 400 que /absences, /stock-groupes, /capacite et /indicators (c'étaient des 500). */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/direction/overview", "/api/v1/direction/breakdown", "/api/v1/direction/report", "/api/v1/direction/gmao",
            "/api/v1/direction/alerts/history"})
    void une_valeur_illisible_est_un_400_bad_request_sur_les_routes_du_tableau_de_bord_comme_sur_les_autres(String chemin) throws Exception {
        ProblemDetail reponse = periodeIllisibleSur(chemin, "ROLE_DIRECTION");

        assertThat(reponse.getStatus()).isEqualTo(400);
        assertThat(reponse.getProperties()).containsEntry("code", "BAD_REQUEST");
        assertThat(reponse.getDetail()).isEqualTo("Paramètre illisible : from");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/direction/overview", "/api/v1/direction/breakdown", "/api/v1/direction/report", "/api/v1/direction/gmao",
            "/api/v1/direction/alerts/history"})
    void un_profil_sans_droit_recoit_403_avant_le_400_sur_les_routes_ajoutees(String chemin) throws Exception {
        ProblemDetail reponse = periodeIllisibleSur(chemin, "ROLE_MEDECIN");

        assertThat(reponse.getStatus()).isEqualTo(403);
        assertThat(reponse.getProperties()).containsEntry("code", "ACCESS_DENIED");
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
