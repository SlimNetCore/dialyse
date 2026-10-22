package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceNonModifiable;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee;
import com.hemodialyse.backend.contexts.absencespatients.domain.SaisieInvalide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus métier du suivi des absences, au format commun de la plateforme ({@code ProblemDetail} avec un {@code code} stable).
 * Valable pour tout contrôleur : l'exclusion d'une séance par la Facturation déclare une absence dans sa propre requête et
 * remonte les mêmes refus. 404 {@code ABSENCE_INTROUVABLE} (absence inconnue ou d'un autre centre, KB-ABS-03), 422 pour une
 * règle violée (le code du legacy est conservé) ou une absence rattrapée ou annulée ({@code ABSENCE_NON_MODIFIABLE}), 409
 * {@code ABSENCE_ECRITURE_CONCURRENTE} pour une écriture concurrente, 400 {@code BAD_REQUEST} pour une valeur inutilisable.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ErreursMetierDesAbsencesAdvice {

    static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(AbsenceIntrouvable.class)
    ProblemDetail absenceIntrouvable(AbsenceIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Absence introuvable", erreur.getMessage(), "ABSENCE_INTROUVABLE");
    }

    @ExceptionHandler(AbsenceNonModifiable.class)
    ProblemDetail absenceNonModifiable(AbsenceNonModifiable erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), "ABSENCE_NON_MODIFIABLE");
    }

    @ExceptionHandler(RegleDAbsenceViolee.class)
    ProblemDetail regleViolee(RegleDAbsenceViolee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), erreur.code());
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ABSENCE_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }
}
