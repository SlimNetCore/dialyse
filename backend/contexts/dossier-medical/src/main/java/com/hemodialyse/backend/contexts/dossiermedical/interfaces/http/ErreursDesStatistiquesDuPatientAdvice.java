package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.FormatDExportInconnu;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeInversee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Les refus des statistiques d'un patient, au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) : 400
 * {@code BAD_REQUEST} (parametre absent ou illisible, corps illisible : memes messages que l'ancienne reponse de la plateforme pour les
 * statistiques medicales, DM8), 400 {@code VALIDATION_ERROR} (centre ou patient absent du corps), 400 {@code INVALID_DATE_RANGE} et
 * {@code INVALID_EXPORT_FORMAT} (codes ET forme {@code {error, code}} reproduits du legacy), 403 {@code ACCESS_DENIED}, 404
 * {@code PATIENT_INTROUVABLE}. Un profil sans droit sur la
 * route recoit 403 AVANT le 400 d'une entree illisible (la lecture du corps et des parametres precede le controle {@code @PreAuthorize}).
 * Aucun message ne reprend de donnee de sante ni de nom de classe.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StatistiquesDuPatientControleur.class)
class ErreursDesStatistiquesDuPatientAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur, HttpServletRequest requete) {
        return entreeIllisible(requete, "Paramètre illisible : " + erreur.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametreAbsent(MissingServletRequestParameterException erreur, HttpServletRequest requete) {
        return entreeIllisible(requete, "Paramètre obligatoire absent : " + erreur.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible(HttpMessageNotReadableException erreur, HttpServletRequest requete) {
        return entreeIllisible(requete, "Corps de la requête illisible");
    }

    private static ProblemDetail entreeIllisible(HttpServletRequest requete, String detail) {
        if (!droitsSuffisants(requete)) {
            return probleme(HttpStatus.FORBIDDEN, "Acces refuse", "Accès refusé", "ACCESS_DENIED");
        }
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", detail, "BAD_REQUEST");
    }

    private static boolean droitsSuffisants(HttpServletRequest requete) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return true;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) return true;
        return DroitsDeLaRoute.suffisants(garde.value(), SecurityContextHolder.getContext().getAuthentication());
    }

    @ExceptionHandler(ValeurHorsBorne.class)
    ProblemDetail valeurHorsBorne(ValeurHorsBorne erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Validation", erreur.getMessage(), "VALIDATION_ERROR");
    }

    /** Comme avant, octet pour octet de forme : {@code application/json} avec {@code error} (message) et {@code code} stable. */
    @ExceptionHandler(PeriodeInversee.class)
    ResponseEntity<Map<String, Object>> periodeInversee(PeriodeInversee erreur) {
        return erreurDeCodeStable(erreur, "INVALID_DATE_RANGE");
    }

    @ExceptionHandler(FormatDExportInconnu.class)
    ResponseEntity<Map<String, Object>> formatDExportInconnu(FormatDExportInconnu erreur) {
        return erreurDeCodeStable(erreur, "INVALID_EXPORT_FORMAT");
    }

    private static ResponseEntity<Map<String, Object>> erreurDeCodeStable(SaisieInvalide erreur, String code) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("error", erreur.getMessage(), "code", code));
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(PatientIntrouvable.class)
    ProblemDetail patientIntrouvable(PatientIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Patient introuvable", erreur.getMessage(), "PATIENT_INTROUVABLE");
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }
}
