package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDejaClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisNonClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MotifDeReouvertureObligatoire;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.ReouvertureRefuseeParLaComptabilite;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus de la clôture mensuelle de facturation (RG-FAC-040 à 045), au format commun de la plateforme ({@code ProblemDetail}
 * avec un {@code code} stable) : 422 {@code PERIODE_DE_FACTURATION_CLOTUREE} (validation, exclusion, réinclusion, changement de
 * forfait d'un mois clos), 409 {@code PERIODE_DEJA_CLOTUREE} et {@code PERIODE_NON_CLOTUREE}, 422 {@code PERIODE_COMPTABLE_CLOTUREE},
 * 400 {@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE}, {@code MOIS_INVALIDE} et {@code ANNEE_INVALIDE}. Limité aux contrôleurs concernés.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {PeriodesDeFacturationControleur.class, ValidationControleur.class, SimulationControleur.class})
class ClotureDeFacturationAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(MoisDeFacturationClos.class)
    ProblemDetail moisClos(MoisDeFacturationClos erreur) {
        ProblemDetail probleme = probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(),
                "PERIODE_DE_FACTURATION_CLOTUREE");
        probleme.setProperty("mois", erreur.mois().toString());
        return probleme;
    }

    @ExceptionHandler(MoisDejaClos.class)
    ProblemDetail dejaClos(MoisDejaClos erreur) {
        return probleme(HttpStatus.CONFLICT, "Periode deja cloturee", erreur.getMessage(), "PERIODE_DEJA_CLOTUREE");
    }

    @ExceptionHandler(MoisNonClos.class)
    ProblemDetail nonClos(MoisNonClos erreur) {
        return probleme(HttpStatus.CONFLICT, "Periode non cloturee", erreur.getMessage(), "PERIODE_NON_CLOTUREE");
    }

    @ExceptionHandler(ReouvertureRefuseeParLaComptabilite.class)
    ProblemDetail comptabiliteCloturee(ReouvertureRefuseeParLaComptabilite erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", erreur.getMessage(), "PERIODE_COMPTABLE_CLOTUREE");
    }

    @ExceptionHandler(MotifDeReouvertureObligatoire.class)
    ProblemDetail motifObligatoire(MotifDeReouvertureObligatoire erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "MOTIF_DE_REOUVERTURE_OBLIGATOIRE");
    }

    @ExceptionHandler(RequeteInvalide.class)
    ProblemDetail requeteInvalide(RequeteInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), erreur.code());
    }
}
