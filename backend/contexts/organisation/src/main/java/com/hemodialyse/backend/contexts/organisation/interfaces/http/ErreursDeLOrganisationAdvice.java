package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.domain.LogoRefuse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Erreurs du contexte Organisation, au format commun de la plateforme :
 * règles métier → 422 avec le détail français historique (contrat legacy conservé),
 * références inconnues → 404. Limité aux contrôleurs du contexte, prioritaire sur
 * le gestionnaire global (qui mettrait tout en 400).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ErreursDeLOrganisationAdvice.class)
class ErreursDeLOrganisationAdvice {

    /**
     * Codes métier (messages d'IllegalArgumentException du service) → détail français legacy.
     * Le legacy levait des BusinessException mappées en 422 avec ces messages exacts.
     */
    private static final Map<String, String> DETAILS_METIER = Map.ofEntries(
            Map.entry("SOCIETE_DERNIER_CENTRE",
                    "Une société doit conserver au moins un centre actif : impossible de désactiver ou retirer son dernier centre"),
            Map.entry("SOCIETE_INTROUVABLE", "Société introuvable"),
            Map.entry("CENTRE_INTROUVABLE", "Centre introuvable dans cette société"),
            Map.entry("SOCIETE_CODE_DEJA_UTILISE", "Ce code de société est déjà utilisé"),
            Map.entry("CENTRE_CODE_DEJA_UTILISE", "Ce code de centre est déjà utilisé"),
            Map.entry("CENTRE_TRANSFERT_MEME_SOCIETE", "Le centre appartient déjà à cette société"),
            Map.entry("CENTRE_DEJA_AFFECTE", "Ce centre est déjà rattaché à cette société"),
            Map.entry("SOCIETE_INACTIVE", "La société de destination est désactivée"),
            Map.entry("SOCIETE_SANS_CENTRE", "Une société doit avoir au moins un centre"),
            Map.entry("SOCIETE_CODE_INVALIDE",
                    "Code de société invalide (2 à 30 caractères : lettres, chiffres, tiret, souligné)"),
            Map.entry("CENTRE_CODE_INVALIDE",
                    "Code de centre invalide (2 à 30 caractères : lettres, chiffres, tiret, souligné)"),
            Map.entry("SOCIETE_NOM_INVALIDE", "Raison sociale obligatoire (200 caractères maximum)"),
            Map.entry("CENTRE_NOM_INVALIDE", "Nom de centre obligatoire (150 caractères maximum)"),
            Map.entry("COORDONNEES_TELEPHONE_INVALIDE", "Numéro de téléphone invalide"),
            Map.entry("COORDONNEES_EMAIL_INVALIDE", "Adresse email invalide"),
            Map.entry("COORDONNEES_SITE_WEB_INVALIDE", "Adresse de site web invalide"),
            Map.entry("SOCIETE_PIED_PAGE_TROP_LONG", "Le pied de page dépasse 500 caractères"),
            Map.entry("SOCIETE_IDENTIFIANT_INVALIDE", "Identifiant trop long (40 caractères maximum)"));

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(statut);
        problem.setTitle(titre);
        problem.setDetail(detail);
        problem.setProperty("code", code);
        return problem;
    }

    /** Verrou de la société tenu par une autre écriture au-delà du délai : 409, à réessayer. */
    @ExceptionHandler(com.hemodialyse.backend.contexts.organisation.domain.EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(com.hemodialyse.backend.contexts.organisation.domain.EcritureConcurrente ex) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente",
                "Une autre ecriture est en cours sur cette societe : reessayer", "ORGANISATION_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(com.hemodialyse.backend.contexts.organisation.domain.LogoIntrouvable.class)
    ProblemDetail logoIntrouvable(com.hemodialyse.backend.contexts.organisation.domain.LogoIntrouvable ex) {
        return probleme(HttpStatus.NOT_FOUND, "Ressource introuvable", "Logo introuvable", "NOT_FOUND");
    }

    /** Élément du centre inexistant (salle, créneau, médecin, machine, protocole) : 404 (exception typée, plus de sous-chaîne). */
    @ExceptionHandler(com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable.class)
    ProblemDetail elementIntrouvable(com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable ex) {
        return probleme(HttpStatus.NOT_FOUND, "Ressource introuvable", ex.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleDomaine(IllegalArgumentException ex) {
        String code = ex.getMessage();
        if (code != null && DETAILS_METIER.containsKey(code)) {
            return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee",
                    DETAILS_METIER.get(code), code);
        }
        if (code != null && code.startsWith("COORDONNEES_")) {
            return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle metier violee", code, code);
        }
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", code, "BAD_REQUEST");
    }

    /** Corps non JSON sur une écriture lue après les droits : 415 (et non 500). */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ProblemDetail typeDeContenuNonSupporte(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return probleme(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporte",
                "Le corps de la requete doit etre du JSON", "UNSUPPORTED_MEDIA_TYPE");
    }

    /**
     * Logo refusé (contenu illisible, trop lourd, type interdit) : 422
     * {@code {violations: [{code, detail}]}, comme le legacy.
     */
    @ExceptionHandler(LogoRefuse.class)
    ResponseEntity<Map<String, List<Map<String, String>>>> logoRefuse(LogoRefuse refuse) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(Map.of("violations",
                List.of(Map.of("code", refuse.code(), "detail", refuse.detail()))));
    }
}
