package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.domain.facture.NumeroDeFactureDejaPris;
import com.hemodialyse.backend.contexts.facturation.domain.facture.SeancesSansForfait;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Les refus propres à l'émission des factures, au format commun de la plateforme ({@code ProblemDetail} avec un {@code code}) :
 * 409 {@code NUMERO_DE_FACTURE_DEJA_PRIS} (KB-FAC-24/25 : le legacy répondait 500) et 422 {@code SEANCES_SANS_FORFAIT}
 * (KB-FAC-32). Limité à la validation ; les autres erreurs suivent la gestion commune.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ValidationControleur.class)
class EmissionDesFacturesAdvice {

    @ExceptionHandler(NumeroDeFactureDejaPris.class)
    ProblemDetail numeroDejaPris(NumeroDeFactureDejaPris erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        probleme.setTitle("Numero de facture deja pris");
        probleme.setDetail(erreur.getMessage());
        probleme.setProperty("code", "NUMERO_DE_FACTURE_DEJA_PRIS");
        return probleme;
    }

    @ExceptionHandler(SeancesSansForfait.class)
    ProblemDetail seancesSansForfait(SeancesSansForfait erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        probleme.setTitle("Regle metier violee");
        probleme.setDetail(erreur.getMessage());
        probleme.setProperty("code", "SEANCES_SANS_FORFAIT");
        probleme.setProperty("seancesSansForfait", erreur.nombre());
        return probleme;
    }
}
