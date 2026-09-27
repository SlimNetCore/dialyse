package com.hemodialyse.backend.infrastructure.web;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Une déconnexion client (page changée, requête annulée, onglet fermé) n'est pas une erreur serveur : elle doit
 * être reconnue quelle que soit sa profondeur dans la chaîne de causes, pour ne jamais noyer les vraies erreurs
 * dans du bruit {@code ERROR} — voir le commentaire de {@link ApiExceptionHandler#isClientDisconnect}.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void recognizes_a_client_abort_wrapped_deep_in_the_cause_chain() {
        Exception wrapped = new HttpMessageNotWritableException("Could not write JSON",
                new RuntimeException("wrapper", new ClientAbortException(new IOException("Broken pipe"))));
        assertTrue(ApiExceptionHandler.isClientDisconnect(wrapped));
    }

    @Test
    void recognizes_asyncRequestNotUsableException() {
        assertTrue(ApiExceptionHandler.isClientDisconnect(new AsyncRequestNotUsableException("not usable")));
    }

    @Test
    void an_ordinary_exception_is_not_a_client_disconnect() {
        assertFalse(ApiExceptionHandler.isClientDisconnect(new IllegalStateException("boom")));
        assertFalse(ApiExceptionHandler.isClientDisconnect(new RuntimeException("boom", new IOException("disk full"))));
    }

    @Test
    void does_not_loop_forever_on_a_self_referential_cause() {
        RuntimeException selfReferential = new RuntimeException("boom") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertFalse(ApiExceptionHandler.isClientDisconnect(selfReferential));
    }

    @Test
    void a_real_error_still_produces_a_500_problem_detail() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("panne réelle"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("panne réelle", problem.getDetail());
    }

    @Test
    void a_client_disconnect_still_returns_a_response_body_harmlessly() {
        // Le corps est calculé quand même (le client ne le lira jamais) : seule la journalisation change de niveau.
        ProblemDetail problem = handler.handleUnexpected(new HttpMessageNotWritableException("x",
                new ClientAbortException(new IOException("Broken pipe"))));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
    }
}
