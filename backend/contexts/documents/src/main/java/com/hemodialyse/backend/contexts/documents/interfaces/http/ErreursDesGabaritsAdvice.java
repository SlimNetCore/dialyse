package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.AccesRefuse;
import com.hemodialyse.backend.contexts.documents.application.GabaritIntrouvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

/**
 * Les refus de la personnalisation des gabarits (DOC3, KB-DOC3-1 à 3), au format commun de la plateforme ({@code ProblemDetail} + {@code code}
 * stable) : 403 {@code ACCESS_DENIED} (centre), 404 sans corps (modèle ou version inexistant, comme avant), 400 {@code BAD_REQUEST}
 * (paramètre illisible, partie {@code file} absente, multipart illisible), 415 (le corps n'est pas du multipart). Le 405 des verbes non gérés
 * est répondu par le contrôleur lui-même (une exception lancée depuis ces méthodes était traitée par le gestionnaire commun : 500).
 * Limité à ce contrôleur ; les refus de profil (403 « Access Denied » de la garde de classe) restent traités par le gestionnaire commun.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = GabaritsControleur.class)
class ErreursDesGabaritsAdvice {

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

    @ExceptionHandler(GabaritIntrouvable.class)
    ResponseEntity<Void> introuvable() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ParametreIllisible.class)
    ProblemDetail parametreIllisible(ParametreIllisible erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Paramètre illisible : " + erreur.nom(), "BAD_REQUEST");
    }

    @ExceptionHandler(PartieAbsente.class)
    ProblemDetail partieAbsente(PartieAbsente erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipartIllisible() {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Requête multipart illisible", "BAD_REQUEST");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail typeDeContenu() {
        return probleme(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporte", "Type de contenu non supporté : multipart/form-data attendu",
                "UNSUPPORTED_MEDIA_TYPE");
    }
}
