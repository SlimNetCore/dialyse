package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ProfilDeLaSynthese;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Requêtes inutilisables sur l'impression de la synthèse (PL8, KB-PL8-6, décision du 2026-11-12) : corps illisible (JSON invalide, tableau,
 * identifiant de centre ou date illisibles) = 400, type de contenu absent ou autre que JSON = 415 (c'étaient des 500), sans message technique.
 * Le profil sans droit reste refusé en 403 AVANT ces erreurs (la lecture du corps précède le contrôle du profil). Limité à ce contrôleur.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SyntheseControleur.class)
class ErreursDeLaSyntheseAdvice {

    @ExceptionHandler({HttpMessageNotReadableException.class, SyntheseRequete.RequeteIllisible.class})
    ProblemDetail corpsIllisible() {
        return sansDroit() ? refus() : probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps de la requête illisible", "BAD_REQUEST");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail typeDeContenu() {
        return sansDroit() ? refus()
                : probleme(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporte", "Type de contenu non supporté", "UNSUPPORTED_MEDIA_TYPE");
    }

    private static boolean sansDroit() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null) {
            return true;
        }
        List<String> roles = authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return !ProfilDeLaSynthese.autorise(roles);
    }

    private static ProblemDetail refus() {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", "Accès refusé", "ACCESS_DENIED");
    }

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }
}
