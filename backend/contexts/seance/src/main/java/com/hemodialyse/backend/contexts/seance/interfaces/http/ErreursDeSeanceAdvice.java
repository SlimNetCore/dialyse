package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.JourDejaAuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeanceDuJourAmbigue;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Les refus de la création, du scan, de la consultation et de la liste des séances, au format commun de la plateforme
 * ({@code ProblemDetail} + {@code code} stable) : 400 {@code BAD_REQUEST} / {@code VALIDATION_ERROR}, 403 {@code ACCESS_DENIED}, 404
 * {@code PATIENT_INTROUVABLE}, 409 {@code SEANCE_DU_JOUR_AMBIGUE} / {@code ECRITURE_CONCURRENTE}, 422 (code du motif de refus). La séance
 * introuvable reste un 400 (reproduit, KB-SEA-04). Aucun message ne reprend de donnée de santé ni de nom de classe (KB-SEA-07).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {SeancesControleur.class, RoutesResiduellesControleur.class})
class ErreursDeSeanceAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(SeanceRefusee.class)
    ProblemDetail seanceRefusee(SeanceRefusee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.motif().code());
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(SeanceIntrouvable.class)
    ProblemDetail seanceIntrouvable(SeanceIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Seance introuvable", "BAD_REQUEST");
    }

    @ExceptionHandler(PatientIntrouvable.class)
    ProblemDetail patientIntrouvable(PatientIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Patient introuvable", erreur.getMessage(), "PATIENT_INTROUVABLE");
    }

    @ExceptionHandler(SeanceDuJourAmbigue.class)
    ProblemDetail seanceDuJourAmbigue(SeanceDuJourAmbigue erreur) {
        return probleme(HttpStatus.CONFLICT, "Seance du jour ambigue", erreur.getMessage(), "SEANCE_DU_JOUR_AMBIGUE");
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(JourDejaAuCalendrier.class)
    ProblemDetail jourDejaAuCalendrier(JourDejaAuCalendrier erreur) {
        return probleme(HttpStatus.CONFLICT, "Jour deja au calendrier", erreur.getMessage(), "JOUR_DEJA_AU_CALENDRIER");
    }

    @ExceptionHandler(SeanceIllisible.class)
    ProblemDetail seanceIllisible(SeanceIllisible erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(ConsommationRefusee.class)
    ProblemDetail consommationRefusee(ConsommationRefusee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }

    /**
     * Corps, paramètre ou contrainte illisible : 400, SAUF si le profil n'a pas le droit d'appeler la route. La lecture du corps précède
     * le contrôle {@code @PreAuthorize} de la méthode ; sans cette garde, un profil refusé recevrait 400 et non 403 (KB-SEA-04).
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail illisible(Exception erreur, HttpServletRequest requete) {
        if (!droitsSuffisants(requete)) return refus();
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps ou paramètre illisible", "BAD_REQUEST");
    }

    /** Paramètre de requête obligatoire absent (par exemple {@code centerId} du détail et de la liste) : 400, 403 avant 400 (KB-SEA-04). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametreAbsent(MissingServletRequestParameterException erreur, HttpServletRequest requete) {
        if (!droitsSuffisants(requete)) return refus();
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Parametre requis absent : " + erreur.getParameterName(), "BAD_REQUEST");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail contrainteViolee(MethodArgumentNotValidException erreur, HttpServletRequest requete) {
        if (!droitsSuffisants(requete)) return refus();
        return probleme(HttpStatus.BAD_REQUEST, "Validation echouee", erreur.getBody().getDetail(), "VALIDATION_ERROR");
    }

    private static ProblemDetail refus() {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", "Accès refusé", "ACCESS_DENIED");
    }

    private static boolean droitsSuffisants(HttpServletRequest requete) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return true;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) return true;
        return DroitsDeLaRoute.suffisants(garde.value(), SecurityContextHolder.getContext().getAuthentication());
    }
}
