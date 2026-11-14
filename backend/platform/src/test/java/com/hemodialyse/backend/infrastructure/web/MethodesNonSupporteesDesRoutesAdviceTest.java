package com.hemodialyse.backend.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KB-DIR-02 (lot L6) : une méthode non supportée sur l'API de la Direction répond 405 {@code METHOD_NOT_ALLOWED} avec l'en-tête
 * {@code Allow} (c'était un 500), sans le message technique ; le reste de l'application garde la réponse du gestionnaire commun.
 */
class MethodesNonSupporteesDesRoutesAdviceTest {

    private final FormesIllisiblesDesRoutesAdvice.MethodesNonSupporteesDesRoutesAdvice advice =
            new FormesIllisiblesDesRoutesAdvice.MethodesNonSupporteesDesRoutesAdvice(new ApiExceptionHandler());

    private ResponseEntity<ProblemDetail> repondre(String methode, String chemin, String... supportees) {
        HttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        return advice.methodeNonSupportee(new HttpRequestMethodNotSupportedException(methode, java.util.List.of(supportees)), requete);
    }

    @Test
    void un_post_sur_la_vue_d_ensemble_est_un_405_avec_la_liste_des_methodes_permises() {
        var reponse = repondre("POST", "/api/v1/direction/overview", "GET", "HEAD");

        assertThat(reponse.getStatusCode().value()).isEqualTo(405);
        assertThat(reponse.getHeaders().getAllow()).extracting(Object::toString).containsExactlyInAnyOrder("GET", "HEAD");
        assertThat(reponse.getBody().getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED");
        assertThat(reponse.getBody().getDetail()).isEqualTo("Méthode non supportée sur cette route");
    }

    @Test
    void le_detail_ne_fuit_pas_le_message_technique_de_spring() {
        var reponse = repondre("DELETE", "/api/v1/direction/snapshots/2026-10", "GET", "POST");

        assertThat(reponse.getBody().getDetail()).doesNotContain("DELETE", "Request method", "not supported");
    }

    @Test
    void une_route_hors_direction_garde_la_reponse_du_gestionnaire_commun() {
        var reponse = repondre("POST", "/api/v1/patients", "GET");

        assertThat(reponse.getStatusCode().value()).isEqualTo(500);
    }
}
