package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.AccesRefuse;
import com.hemodialyse.backend.contexts.documents.application.ModeleEnDoublon;
import com.hemodialyse.backend.contexts.documents.domain.ModeleRefuse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus de la gestion des modèles (DOC4a, KB-DOC4A-1 à 5), au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) :
 * 403 {@code ACCESS_DENIED} (centre), 400 {@code BAD_REQUEST} (paramètre, corps ou modèle refusé), 409 {@code CONFLICT} (code déjà pris),
 * 415 (le corps n'est pas du JSON), 406 (représentation demandée indisponible). Le 405 des verbes non gérés est répondu par le contrôleur
 * lui-même. Limité à ce contrôleur ; les refus de profil (403 « Access Denied » des gardes de méthode) restent traités par le gestionnaire commun.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ModelesControleur.class)
class ErreursDesModelesAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }

    @ExceptionHandler(ParametreIllisible.class)
    ProblemDetail parametreIllisible(ParametreIllisible erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(CorpsIllisible.class)
    ProblemDetail corpsIllisible(CorpsIllisible erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(ModeleRefuse.class)
    ProblemDetail modeleRefuse(ModeleRefuse erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(ModeleEnDoublon.class)
    ProblemDetail modeleEnDoublon(ModeleEnDoublon erreur) {
        return probleme(HttpStatus.CONFLICT, "Conflit", erreur.getMessage(), "CONFLICT");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail typeDeContenu() {
        return probleme(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporte", "Type de contenu non supporté : application/json attendu",
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> representationIndisponible() {
        // Le type du corps est imposé : l'en-tête Accept de la demande ne peut pas être satisfait, c'est précisément la raison du refus.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(probleme(HttpStatus.NOT_ACCEPTABLE, "Representation non disponible", "Aucune représentation acceptable pour cette demande",
                        "NOT_ACCEPTABLE"));
    }
}
