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
}
