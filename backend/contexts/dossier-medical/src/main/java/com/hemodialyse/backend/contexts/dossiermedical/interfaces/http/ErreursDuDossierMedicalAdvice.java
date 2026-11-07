package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.OrdonnanceIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus des ordonnances, prescriptions, demandes d'examen et sérologies (sérologie introuvable : 400 reproduit ; écriture
 * concurrente : 409 {@code SEROLOGIE_ECRITURE_CONCURRENTE}), au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) :
 * 400 {@code BAD_REQUEST} (corps, date, identifiant ou paramètre illisible, ordonnance introuvable : reproduit) ou
 * {@code VALIDATION_ERROR} (valeur hors borne : reproduit), 403 {@code ACCESS_DENIED}, 404 {@code PATIENT_INTROUVABLE} /
 * {@code PRESCRIPTION_INTROUVABLE}, 409 {@code ECRITURE_CONCURRENTE}, 422 (règle de gestion, code de la règle). Aucun message ne
 * reprend de donnée de santé.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {OrdonnancesControleur.class, PrescriptionsControleur.class, AnemieControleur.class,
        DemandesDExamenControleur.class, SerologiesControleur.class})
class ErreursDuDossierMedicalAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(ValeurHorsBorne.class)
    ProblemDetail valeurHorsBorne(ValeurHorsBorne erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Validation", erreur.getMessage(), "VALIDATION_ERROR");
    }

    /**
     * Corps ou paramètre illisible : 400, SAUF si le profil n'a pas le droit d'appeler la route. La lecture du corps précède le contrôle
     * {@code @PreAuthorize} de la méthode ; sans cette garde, un profil refusé recevrait 400 (et non 403) et apprendrait la forme de la
     * route. Les rôles sont ceux de l'annotation {@code @PreAuthorize} du contrôleur ciblé.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail illisible(Exception erreur, HttpServletRequest requete) {
        if (!droitsSuffisants(requete)) {
            return probleme(HttpStatus.FORBIDDEN, "Acces refuse", "Accès refusé", "ACCESS_DENIED");
        }
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps ou paramètre illisible", "BAD_REQUEST");
    }

    private static boolean droitsSuffisants(HttpServletRequest requete) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return true;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) return true;
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null) return false;
        Set<String> possedes = authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        Matcher roles = Pattern.compile("'([A-Z_]+)'").matcher(garde.value());
        while (roles.find()) {
            if (possedes.contains("ROLE_" + roles.group(1))) return true;
        }
        return false;
    }

    @ExceptionHandler(OrdonnanceIntrouvable.class)
    ProblemDetail ordonnanceIntrouvable(OrdonnanceIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(PatientIntrouvable.class)
    ProblemDetail patientIntrouvable(PatientIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Patient introuvable", erreur.getMessage(), "PATIENT_INTROUVABLE");
    }

    @ExceptionHandler(PrescriptionIntrouvable.class)
    ProblemDetail prescriptionIntrouvable(PrescriptionIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Prescription introuvable", erreur.getMessage(), "PRESCRIPTION_INTROUVABLE");
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneDemandeDExamen.class)
    ProblemDetail ecritureConcurrenteSurUneDemandeDExamen(EcritureConcurrenteSurUneDemandeDExamen erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "DEMANDE_EXAMEN_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneSerologie.class)
    ProblemDetail ecritureConcurrenteSurUneSerologie(EcritureConcurrenteSurUneSerologie erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "SEROLOGIE_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(SerologieIntrouvable.class)
    ProblemDetail serologieIntrouvable(SerologieIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(DemandeExamenIntrouvable.class)
    ProblemDetail demandeExamenIntrouvable(DemandeExamenIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(RegleDeGestionViolee.class)
    ProblemDetail regleDeGestion(RegleDeGestionViolee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle de gestion", erreur.getMessage(), erreur.code());
    }
}
