package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.OrdonnanceIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus des ordonnances et des prescriptions, au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) :
 * 400 {@code BAD_REQUEST} (corps, date, identifiant ou paramètre illisible, ordonnance introuvable : reproduit) ou
 * {@code VALIDATION_ERROR} (valeur hors borne : reproduit), 403 {@code ACCESS_DENIED}, 404 {@code PATIENT_INTROUVABLE} /
 * {@code PRESCRIPTION_INTROUVABLE}, 409 {@code ECRITURE_CONCURRENTE}, 422 (règle de gestion, code de la règle). Aucun message ne
 * reprend de donnée de santé.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {OrdonnancesControleur.class, PrescriptionsControleur.class, AnemieControleur.class})
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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail illisible(Exception erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps ou paramètre illisible", "BAD_REQUEST");
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

    @ExceptionHandler(RegleDeGestionViolee.class)
    ProblemDetail regleDeGestion(RegleDeGestionViolee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle de gestion", erreur.getMessage(), erreur.code());
    }
}
