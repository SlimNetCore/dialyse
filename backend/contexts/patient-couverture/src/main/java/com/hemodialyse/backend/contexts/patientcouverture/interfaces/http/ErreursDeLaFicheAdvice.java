package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.AccesRefuse;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus de la fiche du patient, au format commun de la plateforme ({@code ProblemDetail} avec un {@code code} stable) :
 * 400 {@code BAD_REQUEST} (saisie, paramètre, identifiant, corps ou date illisibles), 403 {@code ACCESS_DENIED}, 404
 * {@code PATIENT_INTROUVABLE}, 409 {@code PATIENT_ECRITURE_CONCURRENTE}, 415, 422 (règle violée : code du domaine). Limité aux
 * contrôleurs de la fiche.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {PatientsControleur.class, MouvementsControleur.class, PecControleur.class,
        AttestationsControleur.class, TableauDeBordAdministratifControleur.class})
class ErreursDeLaFicheAdvice {

    static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametreManquant(MissingServletRequestParameterException erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Paramètre obligatoire absent : " + erreur.getParameterName(),
                "BAD_REQUEST");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametreIllisible(MethodArgumentTypeMismatchException erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Paramètre illisible : " + erreur.getName(), "BAD_REQUEST");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpsIllisible(HttpMessageNotReadableException erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps de la requête illisible", "BAD_REQUEST");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail typeDeContenuNonSupporte(HttpMediaTypeNotSupportedException erreur) {
        return probleme(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporte",
                "Le corps de la requête doit être du JSON", "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(RegleDePatientViolee.class)
    ProblemDetail regleViolee(RegleDePatientViolee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(PatientIntrouvable.class)
    ProblemDetail introuvable(PatientIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Patient introuvable", erreur.getMessage(), "PATIENT_INTROUVABLE");
    }

    @ExceptionHandler(com.hemodialyse.backend.contexts.patientcouverture.domain.PecIntrouvable.class)
    ProblemDetail pecIntrouvable(com.hemodialyse.backend.contexts.patientcouverture.domain.PecIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "PEC introuvable", erreur.getMessage(), "PEC_INTROUVABLE");
    }

    @ExceptionHandler(com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationIntrouvable.class)
    ProblemDetail attestationIntrouvable(com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Attestation introuvable", erreur.getMessage(), "ATTESTATION_INTROUVABLE");
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "PATIENT_ECRITURE_CONCURRENTE");
    }
}
