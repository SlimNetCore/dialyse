package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.EquipementInconnu;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipementInconnu;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureInconnue;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePieceInconnu;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs du contexte GMAO, au format commun de la plateforme :
 * types inconnus et équipement inconnu → 400 avec le détail historique exact du legacy
 * (nom de l'énumération legacy, contrat verrouillé par les golden masters).
 * Limité aux contrôleurs du contexte, prioritaire sur le gestionnaire global.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ErreursDeLaGmaoAdvice.class)
class ErreursDeLaGmaoAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    /**
     * Type inconnu : 400 {@code BAD_REQUEST} dont le détail reproduit le texte observé du legacy (KB-GMAO-35 : nom de
     * l'énumération legacy {@code TypeEquipement}), le contrat HTTP étant verrouillé par les golden masters.
     */
    @ExceptionHandler(TypeEquipementInconnu.class)
    ProblemDetail typeEquipementInconnu(TypeEquipementInconnu erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide",
                "No enum constant com.hemodialyse.backend.domain.gmao.model.TypeEquipement." + erreur.code(),
                "BAD_REQUEST");
    }

    /**
     * Nature inconnue : 400 {@code BAD_REQUEST} dont le détail reproduit le texte observé du legacy (KB-GMAO-29 : nom de
     * l'énumération legacy {@code TypeIntervenant}), le contrat HTTP étant verrouillé par les golden masters.
     */
    @ExceptionHandler(NatureInconnue.class)
    ProblemDetail natureInconnue(NatureInconnue erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide",
                "No enum constant com.hemodialyse.backend.domain.gmao.model.TypeIntervenant." + erreur.code(),
                "BAD_REQUEST");
    }

    /**
     * Type de pièce inconnu : 400 {@code BAD_REQUEST} dont le détail reproduit le texte observé du legacy (KB-GMAO-63 : nom de
     * l'énumération legacy {@code TypeDocumentIntervention}), le contrat HTTP étant verrouillé par les golden masters.
     */
    @ExceptionHandler(TypeDePieceInconnu.class)
    ProblemDetail typeDePieceInconnu(TypeDePieceInconnu erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide",
                "No enum constant com.hemodialyse.backend.domain.gmao.model.TypeDocumentIntervention." + erreur.code(),
                "BAD_REQUEST");
    }

    /**
     * Équipement inconnu à l'établissement d'un plan : 400 avec le code stable {@code EQUIPEMENT_INCONNU} (écart décidé
     * KB-GMAO-14 : le legacy répondait 500 par violation de clé étrangère). 400 comme « Plan non trouvé »
     * sur les commandes (KB-GMAO-16) ; même forme de corps que les autres erreurs 400 du legacy.
     */
    @ExceptionHandler(EquipementInconnu.class)
    ProblemDetail equipementInconnu(EquipementInconnu erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), EquipementInconnu.CODE);
    }
}
