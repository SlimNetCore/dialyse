package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.regex.Pattern;

/**
 * Le 400 {@code BAD_REQUEST} des listes de choix (payeurs, forfaits, transports) pour une {@link IllegalArgumentException} (RF5, constat 4
 * de la revue complémentaire RF1-RF3d). Liste blanche de messages ATTENDUS, conservés tels quels (réponses inchangées) : l'identifiant
 * illisible de la conversion ({@code Invalid UUID string: ...}, une seule ligne, 200 caractères au plus) et le paramètre obligatoire absent
 * levé par les contrôleurs. Tout autre message (conversion, SQL, domaine) est remplacé par un texte neutre : un message interne ne sort pas.
 */
final class RefusDeParametre {

    static final String DETAIL_NEUTRE = "Requête invalide";

    private static final Pattern ATTENDU = Pattern.compile("Invalid UUID string: [^\\r\\n]{0,200}|Paramètre obligatoire absent : [A-Za-z]{1,40}");

    private RefusDeParametre() {
    }

    static ProblemDetail reponse(IllegalArgumentException erreur) {
        String message = erreur.getMessage();
        String detail = message != null && ATTENDU.matcher(message).matches() ? message : DETAIL_NEUTRE;
        return ErreursDeLaFicheAdvice.probleme(HttpStatus.BAD_REQUEST, "Requete invalide", detail, "BAD_REQUEST");
    }
}
